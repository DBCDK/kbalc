(ns kbalc.core
  (:gen-class)
  (:require
   [clojure.tools.cli :refer [parse-opts]])
  (:require
   [clojure.pprint :as pp])
  (:import
   [java.util Map])
  (:import
   [java.lang Object String])
  (:import
   [org.apache.kafka.clients.admin
    Admin
    LogDirDescription
    DescribeLogDirsResult
    ReplicaInfo])
  (:import
   [org.apache.kafka.common
    TopicPartition
    TopicPartitionReplica
    ]))

(set! *warn-on-reflection* true)

(def cli-options
  [["-s"
    "--server SERVER"
    "Server with which to bootstrap"]
   ["-p"
    "--port PORT"
    "Port number"
    :default 9092]
   ["-y"
    "--yes"
    "Skip confirmation dialog and start balancing no matter the cost!"]
   ["-b"
    "--broker BROKER"
    "BrokerID of broker to balance"
    :parse-fn #(Integer/parseInt %)]])

;; globals, write-once only in main
;; I don't want to pass them through every function as args.
(def broker)
(def admin)

(defn create-admin [server port]
  (let [ ^java.util.Map cfg {"bootstrap.servers" (str server ":" port)} ]
    (Admin/create cfg)))

(defn get-log-dirs []
  (map
   (fn [[key ^LogDirDescription ldd]]
     (let
         [replica-infos (. ldd (replicaInfos))
          dir key
          partition-count (count replica-infos)
          replicas (map
                    (fn [[^TopicPartition tp ^ReplicaInfo ri]]
                      {:topic (. tp (topic))
                       :partition (. tp (partition))
                       :size-bytes (. ri (size))
                       :is-future (. ri isFuture)})
                    replica-infos)]
       {:replica-infos replica-infos
        :dir dir
        :partition-count partition-count
        :replicas replicas
        :size-bytes (reduce + (map (fn [r] (r :size-bytes)) replicas))}))
   (get (.. ^Admin admin (describeLogDirs [broker]) (allDescriptions) (get)) broker)))

(defn display-balance []
  (let [log-dirs (get-log-dirs)]
    (print (str "Log dirs on broker: '" broker "': "))
    (pp/print-table [:dir :partition-count :size-bytes] (sort-by :dir log-dirs))))

(defn do-balance [threshold]
  "Naively sort logdirs by counting partitions and move partitions from the last to the first"
  (loop [log-dirs (sort-by :partition-count (get-log-dirs))]
    (let [log-S (first log-dirs)
          log-L (last log-dirs)
          ;; no comparing here yet, could use size or other smartness
          part-L (first (log-L :replicas))
          tpr (TopicPartitionReplica. (part-L :topic) (part-L :partition) broker)
          reassignments { tpr (log-S :dir) }
          diff (abs (- (log-L :partition-count) (log-S :partition-count)))]

      (println "")
      (display-balance)
      (println "")

      (if (<= diff threshold)
        (println
         (str "Difference in partition-count between smallest and largest logDir is "
              diff
              " which is less than the threshold of "
              threshold
              "."
              " Stopping kbalc."))
        (do
          (println
           (str "Moving "
                (part-L :topic) "-" (part-L :partition)
                " from " (log-L :dir)
                " to " (log-S :dir)
                ))
          (.. ^Admin admin (alterReplicaLogDirs reassignments) (all) (get))
          (loop [log-dirs' (get-log-dirs)]
            (let [future-replicas (mapcat (fn [ld] (filter (fn [r] (r :is-future)) (ld :replicas))) log-dirs')]
              (when (not-empty future-replicas)
                (println future-replicas)
                (Thread/sleep 1000)
                (recur (get-log-dirs)))))
          (recur (sort-by :partition-count (get-log-dirs))))))))

(defn -main [& args]
  (let [opts ((parse-opts args cli-options) :options)]
    (def broker (opts :broker))
    (def admin (create-admin (opts :server) (opts :port)))
    (if (opts :yes)
      (do-balance 1)
      (do
       (display-balance)
       (println "Will move one partition at a time from a most-populated (by count) logDir to a least-populated logDir (by count)")
       (print "Do you want to balance? Type YES: ")
       (flush)
       (let [response (read-line)]
         (if (not= response "YES")
           (println "Not \"YES\", exiting")
           ((println "Starting balance...")
            (do-balance 1))))))))
