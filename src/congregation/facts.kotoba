(ns congregation.facts
  "Per-jurisdiction religious-organization administrative + child/
  vulnerable-person safeguarding regulatory catalog -- the G2-style
  spec-basis table the Congregational Governance Governor checks
  every assessment/verify proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's religious-
  organization administrative framework and safeguarding law, or did
  it invent one?').

  This catalog deliberately cites ONLY administrative-registration and
  general safeguarding law -- it never cites, models or judges
  doctrine/theology itself, which is outside any government's or this
  actor's competence. `congregation.registry/doctrinal-statement-
  exceeds-core-doctrine?` similarly never judges theological content;
  it only checks a proposed statement's topics against the
  congregation's OWN previously self-declared core-doctrine topic set
  -- a pure self-consistency check, not an external judgment.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official religious-
  corporation administrative authority and general child/vulnerable-
  person safeguarding law (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a
  real source, done -- never invent a jurisdiction's requirements to
  make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  congregant-intake-record/pastoral-assessment-record/safeguarding-
  clearance-record/clergy-credential-verification-record evidence set
  submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:assessment/verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "文化庁 (Agency for Cultural Affairs, religious-corporation registration) / こども家庭庁 (safeguarding)"
          :legal-basis "宗教法人法 (Religious Corporations Act) / 児童虐待の防止等に関する法律 (Child Abuse Prevention Act)"
          :national-spec "宗教法人の登記・運営要件および子ども・脆弱者保護基準"
          :provenance "https://www.bunka.go.jp/seisaku/shukyouhoujin/"
          :required-evidence ["会衆登録記録 (congregant-intake-record)"
                              "牧会評価記録 (pastoral-assessment-record)"
                              "セーフガーディング適格性記録 (safeguarding-clearance-record)"
                              "聖職者資格確認記録 (clergy-credential-verification-record)"]}
   "USA" {:name "United States"
          :owner-authority "State nonprofit/religious-corporation registrars / federal child-safeguarding framework"
          :legal-basis "State nonprofit religious corporation statutes / Child Abuse Prevention and Treatment Act (CAPTA, 42 U.S.C. §5101 et seq.)"
          :national-spec "Religious-corporation registration and child/vulnerable-person safeguarding requirements"
          :provenance "https://www.acf.hhs.gov/cb/law-regulation/capta"
          :required-evidence ["Congregant-intake record"
                              "Pastoral-assessment record"
                              "Safeguarding-clearance record"
                              "Clergy-credential-verification record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Charity Commission for England and Wales / Disclosure and Barring Service (DBS)"
          :legal-basis "Charities Act 2011 / Safeguarding Vulnerable Groups Act 2006"
          :national-spec "Religious-charity governance and safeguarding/DBS-clearance requirements"
          :provenance "https://www.gov.uk/government/organisations/charity-commission"
          :required-evidence ["Congregant-intake record"
                              "Pastoral-assessment record"
                              "Safeguarding-clearance record"
                              "Clergy-credential-verification record"]}
   "DEU" {:name "Germany"
          :owner-authority "Landeskirchenämter / Bundesministerium für Familie, Senioren, Frauen und Jugend (BMFSFJ)"
          :legal-basis "Körperschaftsstatus des öffentlichen Rechts (Art. 140 GG i.V.m. Art. 137 WRV) / Bundeskinderschutzgesetz (BKiSchG)"
          :national-spec "Religionsgemeinschaftsregistrierung und Kinder-/Schutzbefohlenenschutzanforderungen"
          :provenance "https://www.bmfsfj.de/bmfsfj/themen/kinder-und-jugend/kinder--und-jugendschutz"
          :required-evidence ["Gemeindeaufnahmeprotokoll (congregant-intake-record)"
                              "Seelsorgebeurteilung (pastoral-assessment-record)"
                              "Schutzkonzeptfreigabe (safeguarding-clearance-record)"
                              "Klerikerqualifikationsnachweis (clergy-credential-verification-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  pastoral-care referral or publish a doctrinal statement on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9491 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `congregation.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
