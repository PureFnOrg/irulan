(ns org.purefn.irulan.version
  (:gen-class))

(defn -main
  []
  (println (System/getProperty "irulan.version")))
