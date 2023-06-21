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
    KafkaFuture
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

;; TODO: Possible to make these write-once (a la rust Once)? I don't want to
;; pass them through every function as args. For now, promise not to set them
;; again outside of main.
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
                       :size-bytes (. ri (size))})
                    replica-infos)]
       {:replica-infos replica-infos
        :dir dir
        :partition-count partition-count
        :replicas replicas
        :size-bytes (reduce + (map (fn [r] (r :size-bytes)) replicas))}))
   (let [^DescribeLogDirsResult dldr (.. ^Admin admin (describeLogDirs [broker]))]
    (get (.. dldr (allDescriptions) (get)) broker))))

(defn do-balance []
  (let [log-dirs (get-log-dirs)]
    (println "Brrrrrr")))

(defn display-balance []
  (let [log-dirs (get-log-dirs)]
    (println (str "Log dirs on broker: '" broker "': "))
    (pp/print-table [:dir :partition-count :size-bytes] (sort-by :dir log-dirs))))

(defn -main [& args]
  (let [opts ((parse-opts args cli-options) :options)]
    (def broker (opts :broker))
    (def admin (create-admin (opts :server) (opts :port)))
    (display-balance)
    (println "Will move one partition at a time from a most-populated logDir to a least-populated logDir")
    (if (opts :yes)
      (do-balance)
      ((print "Do you want to balance? Type YES: ")
       (flush)
       (let [response (read-line)]
         (if (= response "YES") ((println "Starting balance...")
                                 (do-balance))
             (println "Not \"YES\", exiting")))))))
