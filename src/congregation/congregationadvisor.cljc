(ns congregation.congregationadvisor
  "CongregationOps-LLM client -- the *contained intelligence node* for
  the congregation actor.

  It normalizes matter-intake, drafts a per-jurisdiction religious-
  organization administrative/safeguarding evidence checklist, screens
  matters for an unresolved safeguarding concern, drafts the pastoral-
  care-referral action, and drafts the doctrinal-statement action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real referral finalization/statement
  publication. Every output is censored downstream by `congregation.
  governor` before anything touches the SSoT, and `:actuation/
  finalize-pastoral-referral`/`:actuation/publish-doctrinal-statement`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/finalize-pastoral-referral | :actuation/publish-doctrinal-statement | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [congregation.facts :as facts]
            [congregation.registry :as registry]
            [congregation.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the matter, doctrine topics or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :matter/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-assessment
  "Per-jurisdiction religious-organization administrative/safeguarding
  evidence checklist draft. `:no-spec?` injects the failure mode we
  must defend against: proposing a checklist for a jurisdiction with
  NO official spec-basis in `congregation.facts` -- the Congregational
  Governance Governor must reject this (never invent a jurisdiction's
  requirements)."
  [db {:keys [subject no-spec?]}]
  (let [m (store/matter db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction m))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "congregation.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-safeguarding
  "Safeguarding-concern screening draft. `:safeguarding-concern-
  unresolved?` on the matter record injects the failure mode: the
  Congregational Governance Governor must HOLD, un-overridably, on
  any unresolved concern."
  [db {:keys [subject]}]
  (let [m (store/matter db subject)]
    (cond
      (nil? m)
      {:summary "対象案件記録が見つかりません" :rationale "no matter record"
       :cites [] :effect :safeguarding-screen/set :value {:matter-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:safeguarding-concern-unresolved? m))
      {:summary    (str (:congregant-name m) ": 未解決のセーフガーディング懸念を検出")
       :rationale  "スクリーニングが未解決のセーフガーディング懸念を検出。人手確認とホールドが必須。"
       :cites      [:safeguarding-check]
       :effect     :safeguarding-screen/set
       :value      {:matter-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:congregant-name m) ": 未解決のセーフガーディング懸念なし")
       :rationale  "セーフガーディングスクリーニング完了。"
       :cites      [:safeguarding-check]
       :effect     :safeguarding-screen/set
       :value      {:matter-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-pastoral-referral
  "Draft the actual PASTORAL-CARE-REFERRAL action -- finalizing a real
  referral for a congregant to pastoral care or support. ALWAYS
  `:stake :actuation/finalize-pastoral-referral` -- this is a REAL-
  WORLD congregational act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`congregation.phase`); the governor also always escalates on
  `:actuation/finalize-pastoral-referral`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/matter db subject)]
    {:summary    (str subject " 向け牧会紹介提案"
                      (when m (str " (congregant=" (:congregant-name m) ")")))
     :rationale  (if m
                   "safeguarding-clearance referenced"
                   "案件記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :matter/mark-referred
     :value      {:matter-id subject}
     :stake      :actuation/finalize-pastoral-referral
     :confidence (if m 0.9 0.3)}))

(defn- propose-doctrinal-statement
  "Draft the actual DOCTRINAL-STATEMENT action -- publishing a real
  public doctrinal statement. ALWAYS `:stake :actuation/publish-
  doctrinal-statement` -- this is a REAL-WORLD congregational act,
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`congregation.
  phase`); the governor also always escalates on `:actuation/publish-
  doctrinal-statement`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/matter db subject)]
    {:summary    (str subject " 向け教義声明公開提案"
                      (when m (str " (congregant=" (:congregant-name m) ")")))
     :rationale  (if m
                   (str "statement-topics=" (:statement-topics m)
                        " core-doctrine-topics=" (:core-doctrine-topics m))
                   "案件記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :matter/mark-published
     :value      {:matter-id subject}
     :stake      :actuation/publish-doctrinal-statement
     :confidence (if (and m (not (registry/doctrinal-statement-exceeds-core-doctrine? m))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :matter/intake                          (normalize-intake db request)
    :assessment/verify                      (verify-assessment db request)
    :safeguarding/screen                    (screen-safeguarding db request)
    :actuation/finalize-pastoral-referral    (propose-pastoral-referral db request)
    :actuation/publish-doctrinal-statement   (propose-doctrinal-statement db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは宗教法人の牧会紹介・教義声明公開エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:matter/upsert|:assessment/set|:safeguarding-screen/set|"
       ":matter/mark-referred|:matter/mark-published) "
       ":stake(:actuation/finalize-pastoral-referral か :actuation/publish-doctrinal-statement か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。教義内容そのものを判断・創作しないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :assessment/verify                      {:matter (store/matter st subject)}
    :safeguarding/screen                    {:matter (store/matter st subject)}
    :actuation/finalize-pastoral-referral    {:matter (store/matter st subject)}
    :actuation/publish-doctrinal-statement   {:matter (store/matter st subject)}
    {:matter (store/matter st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Congregational Governance
  Governor escalates/holds -- an LLM hiccup can never auto-finalize a
  referral or auto-publish a doctrinal statement."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :congregationadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
