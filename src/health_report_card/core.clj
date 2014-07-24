(ns health_report_card.core
  (:import  (javancss Javancss Main)
            (net.sourceforge.pmd PMD)
            (net.sourceforge.pmd.cpd CPD CPDCommandLineInterface)
            (java.util Locale)
            (java.io ByteArrayInputStream ByteArrayOutputStream PrintStream))  
  (:require [clojure.pprint :refer :all]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zf]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:gen-class :main true))

(defn capture-console [msg f] 
  (let [bstream (ByteArrayOutputStream.)
        pstream (PrintStream. bstream)
        out (System/out)]
    (log/debug "Starting:" msg)
    (System/setOut pstream)
    (f)
    (System/setOut out)
    (log/debug "Finished:" msg)
    (ByteArrayInputStream. (.toByteArray bstream))))

;Run CPD 
(defn cpd-line-count [srcdir]
  (System/setProperty (CPDCommandLineInterface/NO_EXIT_AFTER_RUN) "true" )
  
  (letfn [(run-cpd [] (CPD/main (into-array ["--files" srcdir "--minimum-tokens" "10" "--format" "xml" "--encoding" "utf-8"])))]  
    
    (try
      (def cpd-seq (xml-seq (xml/parse (capture-console "CPD" run-cpd))))    
      (catch Exception e (do (log/warn e) (log/info "No duplications found") ) (def cpd-seq nil) )) 
    
    ; Duplicates are 10 tokens and 5 or more lines, including whitespace
    {:duplicate-lines-total (apply + (filter #(>= % 5) (for [node cpd-seq :when (= :duplication (:tag node))] (read-string (:lines (:attrs node)))))) } ))


;Run NCSS
(defn ncss-line-count [srcdir] 
  (Locale/setDefault (Locale/US))
  
  (letfn [(run-ncss [] (Javancss. (into-array ["-xml" "-all" srcdir]) Main/S_RCS_HEADER))
          (to-int [seq] (read-string (clojure.string/replace (first seq) #"," "")))]
    (try
      (def ncss-zip (zip/xml-zip (xml/parse (capture-console "NCSS" run-ncss))))    
      (catch Exception e (do (log/warn e) ) (def cpd-seq nil) ))  
    
    { :cyclomatic-complexity-total (apply + (map read-string (zf/xml-> ncss-zip :functions :function :ccn zf/text))) 
      :cyclomatic-complexity-average (to-int (zf/xml-> ncss-zip :functions :function_averages :ccn zf/text ))
      :non-comment-lines-total (to-int (zf/xml-> ncss-zip :functions :ncss zf/text)) })) 

(defn format-num [x]
  (read-string (format "%.2f" (double x))))

;Run PMD
(defn pmd-length [srcdir]
  (letfn [(run-pmd [] (PMD/main (into-array ["-R" "./rulesets/ruleset.xml" "-f" "xml" "-d" srcdir]))) ] 
    (def pmd-seq (xml-seq (xml/parse (capture-console "PMD" run-pmd)))))

  (letfn [ (is-included? [node rule] (and (= :violation (:tag node)) (= rule (:rule (:attrs node)))))
           (length [node] (- (read-string (:endline (:attrs node))) (read-string (:beginline (:attrs node)))))
           (loop-lengths [rule] (for [node pmd-seq :when (is-included? node rule) ]  (length node))) ]
  
    (let [all-methods (loop-lengths "ExcessiveMethodLength")  
          all-classes (loop-lengths "ExcessiveClassLength")]
  
    { :method-length-average (format-num (/ (apply + all-methods) (count all-methods) ))
      :class-length-average (format-num  (/ (apply + all-classes) (count all-classes) )) 
      :lines-total (apply + all-classes) } )))


(defn collect-metrics [srcdir]    
    (let [results (merge (cpd-line-count srcdir) 
                         (ncss-line-count srcdir) 
                         (pmd-length srcdir))]       
      (merge results
             { :duplicate-lines-percentage (format-num (/ (* 100 (:duplicate-lines-total results)) (:lines-total results))) })))

(defn print-results [results]
    (println)    
    (let [duplicate-lines-percentage (format-num (/ (* 100 (:duplicate-lines-total results)) (:lines-total results)))]
      (print-table [{"Metric" "Total lines", "Value" (:lines-total results), "T-shirt size" "MED"}])
      (println)
      (print-table [{"Metric" "Average method length", "Value" (:method-length-average results), "RAG Status" "Green"}
                    {"Metric" "Average class length", "Value" (:class-length-average results), "RAG Status" "Green"}
                    {"Metric" "Average cyclomatic complexity", "Value" (:cyclomatic-complexity-average results), "RAG Status" "Green"}
                    {"Metric" "% Duplication", "Value" duplicate-lines-percentage, "RAG Status" "Red"}]))
  (println)
  (println))

(defn -main [& args] 
  (let [srcdir (first args)]
  (println "Running for: " srcdir)
  (if args
    (print-results (collect-metrics srcdir)  )
    (println "Usage: healthreportcard <source folder>"))))
  


