(ns strictly-specking.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clansi.core :refer [without-ansi]]
            [strictly-specking.core :refer :all :as ss]
            [strictly-specking.error-printing :as ep]
            [strictly-specking.parse-spec :as parse]
            [clojure.spec :as s]))

#_ (remove-ns 'strictly-specking.core-test)

;; load example schemas
(load-file "dev-resources/test_specs/cljs_options_schema.clj")
(load-file "dev-resources/test_specs/test_schema.clj")
(in-ns 'strictly-specking.core-test)

(alias 'c 'strictly-specking.cljs-options-schema)
(alias 't 'strictly-specking.test-schema)


#_(s/explain-data (strict-keys :opt-un [::attic])
                {:attic 1})

(defn problem-map [x]
  (into {}
        (map (juxt :path identity)
             (::s/problems x))))

(deftest children-in-order-test
  ;; this indicates that eliminating-sibling is working
  (is (= (ss/children-in-order ::ss/unknown-key)
         '(:strictly-specking.core/misspelled-key
           :strictly-specking.core/wrong-key
           :strictly-specking.core/misplaced-key)))

  (is (= (ss/total-order ::ss/unknown-key)
         '(:strictly-specking.core/misspelled-key
           :strictly-specking.core/wrong-key
           :strictly-specking.core/misplaced-key)))
 )

(deftest upgrade-error-test
  (is (= (ss/upgrade-error {})
         #::ss{:error-type :strictly-specking.core/bad-value}))

  (is (= (ss/upgrade-error {::ss/error-type ::whatever})
         #::ss{:error-type :strictly-specking.core-test/whatever}))
  )


(defn test-e [sp v]
  (-> (s/explain-data sp v) ::s/problems first
      (assoc ::ss/root-data v)))

(defn prep-e [sp v]
  (let [res (ss/prepare-errors (s/explain-data sp v) v nil)]
    (assert (= 1 (count res)))
    (first res)))

(deftest bad-value-error-test
  (let [err (prep-e (s/map-of keyword? integer?) {:asdf :asdf})]
    (is (= (::ss/error-type err)
           ::ss/bad-value))
    (is (= (::ss/error-path err)
           '{:in-path (:asdf), :error-focus :value}))
    (is (ep/error-message err)) 
    (is (= (ep/inline-message err)
           "The value at key :asdf has a non-conforming value")))

  (let [err (prep-e ::t/figwheel-options {:css-dirs true})]
    (is (= (::ss/error-type err)
           ::ss/bad-value))
    (is (= (::ss/error-path err)
           '{:in-path (:css-dirs), :error-focus :value}))
    (is (ep/error-message err))
    (is (= (ep/inline-message err)
           "The value at key :css-dirs has a non-conforming value"))
    (is (= (ss/keys-to-document err)
           [::t/css-dirs]))
    (is
     (.contains (with-out-str (without-ansi (ep/pprint-inline-message err)))
                ":css-dirs"))
    (is (= (keys-to-document err)
           [::t/css-dirs])) 
    
    ))

(deftest attach-reason-error-test
  (let [err (prep-e (ss/attach-reason "asdf" (fn [x] false)
                                      :focus-key :asdf)
                    {})]
    
    (is (= (::ss/error-type err)
           ::ss/attach-reason))
    (is (= (::ss/attach-reason err)
           "asdf"))
    (is (= (::ss/error-path err)
           '{:in-path (:asdf), :error-focus :key, :missing-key true}))
    (is (ep/error-message err)) 
    (is (= (ep/inline-message err) "asdf"))
    (is (not (string/blank? (with-out-str (ep/pprint-inline-message err)))))
    
    )

  (let [err (prep-e ::t/compiler {:output-to "main.js"
                                  :source-map "asdf"
                                  :optimizations :none})]
    (is (= (::ss/error-type err)
           ::ss/attach-reason))
    (is (= (::ss/attach-reason err)
           ":source-map must be a boolean when :optimizations is :none"))
    (is (= (::ss/error-path err)
             '{:in-path (:source-map), :error-focus :key, :missing-key false}))
    (is (ep/error-message err)) 
    (is (= (ep/inline-message err) ":source-map must be a boolean when :optimizations is :none"))
    (is (not (string/blank? (with-out-str (ep/pprint-inline-message err)))))
      
      )
  )

(deftest wrong-size-collection-test
  (is (= (ss/wrong-size-pred? '(clojure.core/<= 2 (clojure.core/count %) Integer/MAX_VALUE))
         #::ss{:min-count 2, :max-count 'Integer/MAX_VALUE}))
  (is (nil?
       (ss/wrong-size-pred? '(clojure.core/<= 2 rr (clojure.core/count %) Integer/MAX_VALUE))))
  (let [e (prep-e (s/every integer? :min-count 2) [])]
    (is (= (::ss/error-type e)
           ::ss/wrong-size-collection))
    (is (= (::ss/min-count e) 2))
    (is (= (::ss/error-path e) '{:in-path (), :error-focus :value}))
    (is (ep/error-message e))
    ))


(deftest wrong-count-collection-test
  (is (= (ss/wrong-count-pred? '(clojure.core/= 2 (clojure.core/count %)))
         #:strictly-specking.core{:target-count 2}))
  (is (nil? (ss/wrong-count-pred? '(clojure.core/= 2 5 (clojure.core/count %)))))
  
  (let [e (prep-e (s/map-of keyword? (s/every integer? :count 2))
                  {:data-key []})]
    (is (= (::ss/error-path e)
           '{:in-path (:data-key), :error-focus :value}))
    (is (= (::ss/target-count e) 2))
    (is (= (::ss/error-type e)
           ::ss/wrong-count-collection))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The collection should have exactly 2 values"))
    )
  )

(deftest should-not-be-empty-test
  
  (let [e (prep-e ::t/figwheel-options {:css-dirs []})]
    (is (= (::ss/error-type e)
           ::ss/should-not-be-empty))
    (is (= (::ss/error-path e)
           '{:in-path (:css-dirs), :error-focus :value}))
    (is (= (ss/keys-to-document e)
           [::t/css-dirs]))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The value at key :css-dirs should not be empty"))
    
    )

  )


(deftest missing-required-keys-test
  (let [e (prep-e ::t/build-config {})]
    (is (= (::ss/error-type e)
           ::ss/missing-required-keys))
    (is (= (::ss/error-path e)
           '{:in-path (:source-paths), :error-focus :key, :missing-key true}))
    (is (= (::ss/missing-keys e)
           [:source-paths :compiler]))
    (is (= (ss/keys-to-document e)
           [:strictly-specking.test-schema/compiler
            :strictly-specking.test-schema/source-paths]))
    (is (ep/error-message e))
    ;; there is no inline message
    ;; (is (ep/inline-message e))
    )
  )


(deftest bad-key-test
  (let [e (prep-e ::t/figwheel-options {:hawk-options {3 :java}})]
    (is (= (::ss/error-type e)
           ::ss/bad-key))
    (is (= (::ss/error-path e)
           '{:in-path (:hawk-options 3), :error-focus :key}))
    (is (= (ss/keys-to-document e)
           [:strictly-specking.test-schema/hawk-options]))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The key 3 does not conform.")))
  )


(deftest unknown-key-test
  (let [e (prep-e :figwheel.lein-project/figwheel {:badland-is-bad :fouth-of-july})]
    (is (= (::ss/error-type e)
           ::ss/unknown-key))
    (is (= (::ss/error-path e)
           '{:in-path (:badland-is-bad), :error-focus :key}))
    (is (nil? (ss/keys-to-document e)))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The key :badland-is-bad is unrecognized")))

  )

(deftest misspelled-key-test
  (let [e (prep-e :figwheel.lein-project/figwheel {:sevrer-i "123.123"})]
    (is (= (::ss/error-type e)
           ::ss/misspelled-key))
    (is (= (::ss/correct-key e)
           :server-ip))
    (is (= (::ss/error-path e)
           '{:in-path (:sevrer-i), :error-focus :key}))
    (is (= (ss/keys-to-document e)
           [::t/server-ip]))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
             "The key :sevrer-i should probably be :server-ip")))
  )


(deftest wrong-key-test
  (let [e (prep-e :figwheel.lein-project/figwheel {:adfafa {:watcher :java}})]
    (is (= (::ss/error-type e)
           ::ss/wrong-key))
    (is (= (::ss/correct-key e)
           :hawk-options))
    (is (= (::ss/error-path e)
           '{:in-path (:adfafa), :error-focus :key}))
    (is (= (ss/keys-to-document e)
           [::t/hawk-options]))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The key :adfafa should probably be :hawk-options")))
  )

(deftest misplaced-key-test
  (let [v {:cljsbuild
           {:builds
            {:dev
             {:source-paths ["src"]
              :compiler
              {:figwheel
               {:websocket-host "localhost"
                :on-jsload      'example.core/fig-reload
                :on-message     'example.core/on-message
                :source-map true
                :debug true}}}}}}
        e (prep-e :strictly-specking.test-schema/lein-project-with-cljsbuild v)]
    (is (= (::ss/error-type e)
           ::ss/misplaced-key))
    (is (= (::ss/unknown-key e)
           :figwheel))
    (is (= (::ss/suggested-path e)
           '(:cljsbuild :builds :dev :figwheel)))
    (is (= (::ss/error-path e)
           '{:in-path (:cljsbuild :builds :dev :compiler :figwheel), :error-focus :key}))
    (is (= (::ss/document-keys e)
           [::t/figwheel]))
    (is (= (ss/keys-to-document e)
           [::t/figwheel]))
    (is (ep/error-message e))
    (is (= (ep/inline-message e)
           "The key :figwheel has been misplaced")))  

  )


(deftest find-keypath-without-ns-test
  (is (= (parse/find-key-path-without-ns :cljsbuild.lein-project.require-builds/cljsbuild
                                         :figwheel)
         #{'({:ky-spec :strictly-specking.test-schema/builds, :ky :builds}
            {:ky :strictly-specking.core/int-key}
            {:ky-spec :strictly-specking.test-schema/figwheel, :ky :figwheel})
           '({:ky-spec :strictly-specking.test-schema/builds, :ky :builds}
            {:ky :strictly-specking.core/pred-key,
             :ky-pred-desc :strictly-specking.test-schema/string-or-named}
            {:ky-spec :strictly-specking.test-schema/figwheel, :ky :figwheel})}))
  )


(deftest specific-paths-filter-general-test
  (is (= (ss/specific-paths-filter-general [{::error-path {:in-path [1 2 3 ]}}
                                         {::error-path {:in-path [1 2 3 4]}} ])
         '({:strictly-specking.core-test/error-path {:in-path [1 2 3]}})))
  )



(deftest describe-expections-for-path-finding

  (is (= (s/describe (s/keys :opt-un [::a ::b]
                             :opt [::c ::d]))
         '(keys
           :opt
           [::c ::d]
           :opt-un
           [::a ::b])))

  (is (= (s/describe (strict-keys :opt-un [::a ::b]
                                  :opt [::c ::d]))
         '(strict-keys
           :opt
           [::c ::d]
           :opt-un
           [::a ::b])))

  (is  (parse/expanded-map-of-desc? (s/describe (s/map-of keyword? even?))))

  (is (= (s/describe (s/or :yep (s/map-of keyword? even?)))
         '(or :yep (map-of keyword? even?))))

  (is (= (s/describe (s/and (s/map-of keyword? even?)))
         '(and (map-of keyword? even?))))

  (is (= (take 2 (s/describe (s/coll-of keyword?)))
         '(every keyword?)))

  (is (= (s/describe (s/and (s/coll-of keyword?)))
         '(and (coll-of keyword?))))

  (is (= (s/describe (s/+ even?))
         '(+ even?)))
  (is (= (s/describe (s/* even?))
         '(* even?)))
  (is (= (s/describe (s/? even?))
         '(? even?)))

  )

;; TODO more pathfinding and parsing tests
#_(deftest pathfinding
    (is (= (parse/find-key-path ::root ::bedroom)
           #{(list {:ky-spec ::cljsbuild, :ky :cljsbuild}
                   {:ky-spec ::houses, :ky :houses}
                   {:ky :strictly-specking.core/int-key}
                   {:ky-spec ::bedroom, :ky :bedroom})
             (list {:ky-spec ::cljsbuild, :ky :cljsbuild}
                   {:ky-spec ::houses, :ky :houses}
                   {:ky :strictly-specking.core/int-key}                 
                   {:ky-spec ::attic, :ky :attic}
                   {:ky-spec ::bedroom, :ky :bedroom})}))
    )


(def not-blank? (complement string/blank?))

(deftest verify-warnings-and-reasons
  ;; no warnings or errors on a perectly valid empty options
  (is (s/valid? ::c/compiler-options {}))
  (is (string/blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"}))))
  
  (testing "warnings produce warnings and are still valid"
    ;; no warning produced
    ;; :asset-path
    (is (not-blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"
                                                                :asset-path "asdf/asdf"}))))
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                      :asset-path "asdf/asdf"}))
    
    ;; pseudo-names
    (is (not-blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"
                                                                :pseudo-names true}))))
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                      :pseudo-names true}))
    
    ;; preamble
    (is (not-blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"
                                                                :preamble ["asdf"] :optimizations :advanced}))))
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                      :preamble ["asdf"] :optimizations :advanced}))
    
    ;; hash-bang
    (is (not-blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"
                                                                :hashbang true}))))
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                        :hashbang true}))
    
    ;; clojure-defines
    (is (not-blank? (with-out-str (s/valid? ::c/compiler-options {:output-to "main.js"
                                                                :closure-defines {'goog.DEBUG false}
                                                                :optimizations :whitespace}))))
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                      :closure-defines {'goog.DEBUG false}
                                      :optimizations :whitespace}))
    
    )
  
  (testing "fail on attach-reason predicates"
    (is (not (s/valid? ::c/compiler-options {:output-to "main.js"
                                           :closure-defines {'goog.DEBUG false}})))
    (is (not (s/valid? ::c/compiler-options {:output-to "main.js"
                                           :source-map "asdf"})))
    (is (not (s/valid? ::c/compiler-options {:output-to "main.js"
                                           :source-map false :optimizations :advanced})))
    
    (is (s/valid? ::c/compiler-options {:output-to "main.js"
                                      :optimizations :advanced}))
    )
  
)




















(comment
  (s/def ::id (s/or :string string? :keyword keyword?))
(s/def ::source-paths (s/+ string?))
(s/def ::assert #(or (true? %) (false? %)))
(s/def ::build-config (strict-keys :req-un [
                                            ::id
                                            ::source-paths]
                                   :opt-un [::assert]))
    
(s/def ::http-server-root string?)
(s/def ::server-port      integer?)
(s/def ::server-ip        string?)
(s/def ::builds           (s/* ::build-config))
    
(s/def ::figwheel (strict-keys
                   :opt-un [::http-server-root
                            ::server-port
                            ::server-ip]
                   :req-un [::builds]))

(s/def ::dimensions (s/tuple integer? integer?))

(s/def ::bedroom
  (strict-keys :opt-un [::door
                        ::dimensions]))

(s/def ::attic (strict-keys
                :opt-un [::bedroom
                         ::bathroom]))

(s/def ::windows integer?)
(s/def ::door boolean?)
(s/def ::name string?)

(s/def ::house (strict-keys
                :opt-un [::windows
                         ::attic
                         ::bedroom
                         ::bathroom]
                :req-un [::name 
                         ::door]))

(s/def ::houses (s/+ ::house))

(s/def ::cljsbuild
  (strict-keys
   :req-un [::houses]))

(s/def ::root (strict-keys
               :opt-un [::cljsbuild
                        ::figwheel]))
)
