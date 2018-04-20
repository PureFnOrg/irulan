(ns org.purefn.irulan.common
  "Common, reusable, domain-independent specs."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]])
  (:import [org.apache.commons.validator.routines EmailValidator]))

;;------------------------------------------------------------------------------
;; Utility.
;;------------------------------------------------------------------------------

(def integer-string?
  (s/spec (s/and string?
                 (fn [s] (try (Long. s)
                              true
                              (catch Exception _ false))))
          :gen (gen/fmap str (gen/int))))

(defn integer-string-between?
  "A predicate (not spec), true if s is a string expressing an integer
   between x (inclusive) and y (exclusive)."
  [s x y]
  (when-let [num (try (Long. s)
                      (catch Exception _ nil))]
    (and (>= num x)
         (< num y))))



;;------------------------------------------------------------------------------
;; Predicates.
;;------------------------------------------------------------------------------

;; Base64.
(def base64-regex #"[a-zA-Z0-9+/]+[=]{0,2}")

(def base64?
  (s/spec (s/and string? (partial re-matches base64-regex))
          :gen #(string-from-regex base64-regex)))


;; Git Commit Hash (first 6 digits)
(def commit-regex #"[0-9a-f]{6}")

(def commit?
  (s/spec (s/and string? (partial re-matches commit-regex))
          :gen #(string-from-regex commit-regex)))

(def release-regex #"^((0|[1-9][0-9]*)\.){1,}(0|[1-9][0-9]*)(-SNAPSHOT)?$")

(def release?
  (s/spec (s/and string? (partial re-matches release-regex))
          :gen #(-> (gen/choose 0 100)
                    (->> (gen/fmap str))
                    (gen/vector 2 4)
                    (->> (gen/fmap (partial str/join ".")))
                    (gen/tuple (gen/boolean))
                    (->> (gen/fmap (fn [[version snapshot?]]
                                     (if snapshot?
                                       (str version "-SNAPSHOT")
                                       version)))))))

(def version? (s/or :commit commit? :release release?))


;; IP Addresses.
(def ip4-addr-regex #"^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$")

(def ip4-addr?
  (s/spec (s/and string? (partial re-matches ip4-addr-regex)
                 (fn [s]
                   (when-let [ss (str/split s #"\.")]
                     (every? #(try (<= 0 (Integer/parseInt %) 255)
                                   (catch Exception ex false))
                             ss))))
          :gen #(-> (gen/choose 0 255)
                    (->> (gen/fmap str))
                    (gen/vector 4)
                    (->> (gen/fmap (partial str/join "."))))))


;; Fully Qualified Domain Names.
(def fqdn-regex #"[a-z][a-z0-9-]*[a-z0-9](\.[a-z][a-z0-9-]*[a-z0-9])*")

(def fqdn?
  (s/spec (s/and string? (partial re-matches fqdn-regex))
          :gen #(-> (gen/string-alphanumeric)
                    (->> (gen/fmap str/lower-case))
                    (gen/not-empty)
                    (gen/vector 1 2)
                    (->> (gen/fmap (partial str/join "-")))
                    (gen/vector 1 3)
                    (->> (gen/fmap (partial str/join "."))))))


;; Host Identification.
(def host? (s/or :fqdn fqdn? :ip-addr ip4-addr?))

(def port? (s/int-in 0 65535))

(def port-string?
  (s/spec #(integer-string-between? % 0 65535)
          :gen #(gen/fmap str (s/gen (s/int-in 0 65535)))))

(def host-port?
   "Spec for a host:port string, where port is optional."
  (s/spec #(let [[host port] (str/split % #":")]
             (s/valid? host? host) (s/valid? port-string? port))
          :gen #(gen/fmap (partial str/join ":")
                          (gen/tuple (s/gen host?) (s/gen port-string?)))))

;; Time stamp (milliseconds since 1970)
(def timestamp?
  (s/spec (s/and integer? pos?)
          :gen #(gen/large-integer* {:min 1483228800})))

;; guids

(def guid-regex #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

(def valid-guid?
  (s/with-gen
    #(re-matches guid-regex %)
    #(string-from-regex guid-regex)))

;; emails

(def email-regex
  "Pattern used to generate emails which pass the apache commons validator spec."
  (re-pattern "[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))"))

(def email?
  (s/spec
   #(.isValid (EmailValidator/getInstance) %)
   :gen #(string-from-regex (re-pattern email-regex))))

;; Event and Command Hander Names.
(def handler-regex #"[a-z][a-z-]*[a-z]")

(def handler?
  (s/spec (s/and string? (partial re-matches handler-regex))
          :gen #(string-from-regex handler-regex)))
