(ns congregation.store
  "SSoT for the congregation actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/congregation/store_contract_test.clj), which is the whole
  point: the actor, the Congregational Governance Governor and the
  audit ledger never know which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (finalizing a pastoral-care referral, publishing a
  doctrinal statement) acting on the SAME entity (a matter), each with
  its OWN history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:pastoral-referral-finalized?`/
  `:doctrinal-statement-published?`, never a `:status` value) -- the
  same discipline every prior sibling governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which matter was
  screened for an unresolved safeguarding concern, which pastoral
  referral was finalized, which doctrinal statement was published, on
  what jurisdictional basis, approved by whom' is always a query over
  an immutable log -- the audit trail a congregant trusting a
  congregation needs, and the evidence a congregation needs if a
  referral or statement decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [congregation.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (matter [s id])
  (all-matters [s])
  (safeguarding-screen-of [s matter-id] "committed safeguarding-concern screening verdict for a matter, or nil")
  (assessment-of [s matter-id] "committed pastoral assessment, or nil")
  (ledger [s])
  (referral-history [s] "the append-only pastoral-referral history (congregation.registry drafts)")
  (statement-history [s] "the append-only doctrinal-statement history (congregation.registry drafts)")
  (next-referral-sequence [s jurisdiction] "next referral-number sequence for a jurisdiction")
  (next-statement-sequence [s jurisdiction] "next statement-number sequence for a jurisdiction")
  (matter-already-referred? [s matter-id] "has this matter's pastoral referral already been finalized?")
  (matter-already-published? [s matter-id] "has this matter's doctrinal statement already been published?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-matters [s matters] "replace/seed the matter directory (map id->matter)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained matter set covering both actuation
  lifecycles (finalizing a pastoral referral, publishing a doctrinal
  statement) so the actor + tests run offline."
  []
  {:matters
   {"matter-1" {:id "matter-1" :congregant-name "Sakura Community Congregation"
                :statement-topics #{:worship-schedule}
                :core-doctrine-topics #{:worship-schedule :sacraments}
                :safeguarding-concern-unresolved? false
                :pastoral-referral-finalized? false :doctrinal-statement-published? false
                :jurisdiction "JPN" :status :intake}
    "matter-2" {:id "matter-2" :congregant-name "Atlantis Fellowship"
                :statement-topics #{:worship-schedule}
                :core-doctrine-topics #{:worship-schedule :sacraments}
                :safeguarding-concern-unresolved? false
                :pastoral-referral-finalized? false :doctrinal-statement-published? false
                :jurisdiction "ATL" :status :intake}
    "matter-3" {:id "matter-3" :congregant-name "鈴木教会"
                :statement-topics #{:worship-schedule :governance-succession}
                :core-doctrine-topics #{:worship-schedule :sacraments}
                :safeguarding-concern-unresolved? false
                :pastoral-referral-finalized? false :doctrinal-statement-published? false
                :jurisdiction "JPN" :status :intake}
    "matter-4" {:id "matter-4" :congregant-name "田中教会"
                :statement-topics #{:worship-schedule}
                :core-doctrine-topics #{:worship-schedule :sacraments}
                :safeguarding-concern-unresolved? true
                :pastoral-referral-finalized? false :doctrinal-statement-published? false
                :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-pastoral-referral!
  "Backend-agnostic `:matter/mark-referred` -- looks up the matter via
  the protocol and drafts the pastoral-referral record, and returns
  {:result .. :matter-patch ..} for the caller to persist."
  [s matter-id]
  (let [m (matter s matter-id)
        seq-n (next-referral-sequence s (:jurisdiction m))
        result (registry/register-pastoral-referral matter-id (:jurisdiction m) seq-n)]
    {:result result
     :matter-patch {:pastoral-referral-finalized? true
                   :referral-number (get result "referral_number")}}))

(defn- publish-doctrinal-statement!
  "Backend-agnostic `:matter/mark-published` -- looks up the matter via
  the protocol and drafts the doctrinal-statement record, and returns
  {:result .. :matter-patch ..} for the caller to persist."
  [s matter-id]
  (let [m (matter s matter-id)
        seq-n (next-statement-sequence s (:jurisdiction m))
        result (registry/register-doctrinal-statement matter-id (:jurisdiction m) seq-n)]
    {:result result
     :matter-patch {:doctrinal-statement-published? true
                   :statement-number (get result "statement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (matter [_ id] (get-in @a [:matters id]))
  (all-matters [_] (sort-by :id (vals (:matters @a))))
  (safeguarding-screen-of [_ id] (get-in @a [:safeguarding-screens id]))
  (assessment-of [_ matter-id] (get-in @a [:assessments matter-id]))
  (ledger [_] (:ledger @a))
  (referral-history [_] (:referrals @a))
  (statement-history [_] (:statements @a))
  (next-referral-sequence [_ jurisdiction] (get-in @a [:referral-sequences jurisdiction] 0))
  (next-statement-sequence [_ jurisdiction] (get-in @a [:statement-sequences jurisdiction] 0))
  (matter-already-referred? [_ matter-id] (boolean (get-in @a [:matters matter-id :pastoral-referral-finalized?])))
  (matter-already-published? [_ matter-id] (boolean (get-in @a [:matters matter-id :doctrinal-statement-published?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :matter/upsert
      (swap! a update-in [:matters (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :safeguarding-screen/set
      (swap! a assoc-in [:safeguarding-screens (first path)] payload)

      :matter/mark-referred
      (let [matter-id (first path)
            {:keys [result matter-patch]} (finalize-pastoral-referral! s matter-id)
            jurisdiction (:jurisdiction (matter s matter-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:referral-sequences jurisdiction] (fnil inc 0))
                       (update-in [:matters matter-id] merge matter-patch)
                       (update :referrals registry/append result))))
        result)

      :matter/mark-published
      (let [matter-id (first path)
            {:keys [result matter-patch]} (publish-doctrinal-statement! s matter-id)
            jurisdiction (:jurisdiction (matter s matter-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:statement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:matters matter-id] merge matter-patch)
                       (update :statements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-matters [s matters] (when (seq matters) (swap! a assoc :matters matters)) s))

(defn seed-db
  "A MemStore seeded with the demo matter set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :safeguarding-screens {} :ledger [] :referral-sequences {}
                           :referrals [] :statement-sequences {} :statements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/safeguarding-screen payloads,
  statement-topics/core-doctrine-topics sets, ledger facts, referral/
  statement records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:matter/id                         {:db/unique :db.unique/identity}
   :assessment/matter-id              {:db/unique :db.unique/identity}
   :safeguarding-screen/matter-id     {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :referral/seq                      {:db/unique :db.unique/identity}
   :statement/seq                     {:db/unique :db.unique/identity}
   :referral-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :statement-sequence/jurisdiction   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- matter->tx [{:keys [id congregant-name statement-topics core-doctrine-topics
                          safeguarding-concern-unresolved?
                          pastoral-referral-finalized? doctrinal-statement-published?
                          jurisdiction status referral-number statement-number]}]
  (cond-> {:matter/id id}
    congregant-name                             (assoc :matter/congregant-name congregant-name)
    statement-topics                            (assoc :matter/statement-topics (enc statement-topics))
    core-doctrine-topics                        (assoc :matter/core-doctrine-topics (enc core-doctrine-topics))
    (some? safeguarding-concern-unresolved?)     (assoc :matter/safeguarding-concern-unresolved? safeguarding-concern-unresolved?)
    (some? pastoral-referral-finalized?)        (assoc :matter/pastoral-referral-finalized? pastoral-referral-finalized?)
    (some? doctrinal-statement-published?)      (assoc :matter/doctrinal-statement-published? doctrinal-statement-published?)
    jurisdiction                                (assoc :matter/jurisdiction jurisdiction)
    status                                      (assoc :matter/status status)
    referral-number                             (assoc :matter/referral-number referral-number)
    statement-number                            (assoc :matter/statement-number statement-number)))

(def ^:private matter-pull
  [:matter/id :matter/congregant-name :matter/statement-topics :matter/core-doctrine-topics
   :matter/safeguarding-concern-unresolved? :matter/pastoral-referral-finalized? :matter/doctrinal-statement-published?
   :matter/jurisdiction :matter/status :matter/referral-number :matter/statement-number])

(defn- pull->matter [m]
  (when (:matter/id m)
    {:id (:matter/id m) :congregant-name (:matter/congregant-name m)
     :statement-topics (dec* (:matter/statement-topics m))
     :core-doctrine-topics (dec* (:matter/core-doctrine-topics m))
     :safeguarding-concern-unresolved? (boolean (:matter/safeguarding-concern-unresolved? m))
     :pastoral-referral-finalized? (boolean (:matter/pastoral-referral-finalized? m))
     :doctrinal-statement-published? (boolean (:matter/doctrinal-statement-published? m))
     :jurisdiction (:matter/jurisdiction m) :status (:matter/status m)
     :referral-number (:matter/referral-number m) :statement-number (:matter/statement-number m)}))

(defrecord DatomicStore [conn]
  Store
  (matter [_ id]
    (pull->matter (d/pull (d/db conn) matter-pull [:matter/id id])))
  (all-matters [_]
    (->> (d/q '[:find [?id ...] :where [?e :matter/id ?id]] (d/db conn))
         (map #(pull->matter (d/pull (d/db conn) matter-pull [:matter/id %])))
         (sort-by :id)))
  (safeguarding-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?k :safeguarding-screen/matter-id ?mid] [?k :safeguarding-screen/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ matter-id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?a :assessment/matter-id ?mid] [?a :assessment/payload ?p]]
              (d/db conn) matter-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (referral-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :referral/seq ?s] [?e :referral/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (statement-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :statement/seq ?s] [?e :statement/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-referral-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :referral-sequence/jurisdiction ?j] [?e :referral-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-statement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :statement-sequence/jurisdiction ?j] [?e :statement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (matter-already-referred? [s matter-id]
    (boolean (:pastoral-referral-finalized? (matter s matter-id))))
  (matter-already-published? [s matter-id]
    (boolean (:doctrinal-statement-published? (matter s matter-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :matter/upsert
      (d/transact! conn [(matter->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/matter-id (first path) :assessment/payload (enc payload)}])

      :safeguarding-screen/set
      (d/transact! conn [{:safeguarding-screen/matter-id (first path) :safeguarding-screen/payload (enc payload)}])

      :matter/mark-referred
      (let [matter-id (first path)
            {:keys [result matter-patch]} (finalize-pastoral-referral! s matter-id)
            jurisdiction (:jurisdiction (matter s matter-id))
            next-n (inc (next-referral-sequence s jurisdiction))]
        (d/transact! conn
                     [(matter->tx (assoc matter-patch :id matter-id))
                      {:referral-sequence/jurisdiction jurisdiction :referral-sequence/next next-n}
                      {:referral/seq (count (referral-history s)) :referral/record (enc (get result "record"))}])
        result)

      :matter/mark-published
      (let [matter-id (first path)
            {:keys [result matter-patch]} (publish-doctrinal-statement! s matter-id)
            jurisdiction (:jurisdiction (matter s matter-id))
            next-n (inc (next-statement-sequence s jurisdiction))]
        (d/transact! conn
                     [(matter->tx (assoc matter-patch :id matter-id))
                      {:statement-sequence/jurisdiction jurisdiction :statement-sequence/next next-n}
                      {:statement/seq (count (statement-history s)) :statement/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-matters [s matters]
    (when (seq matters) (d/transact! conn (mapv matter->tx (vals matters)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:matters ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [matters]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-matters s matters))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo matter set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
