(ns directionalsurvey.system
  (:require [taoensso.sente :as sente]
            [clojure.tools.logging :as log]
            [datomic.api :as d :refer [db q]]))

;datomic setup
(defn create-db [url]
  (d/create-database url)
  (let [schema (read-string (slurp "resources/directionalsurvey.edn"))
        conn (d/connect url)]
    (d/transact conn schema)
    {:db-connection conn
     :change-queue (d/tx-report-queue conn)}))

;sente setup, This function will be called whenever a new channel is open
(defn- get-user-id [request] 
  (str (java.util.UUID/randomUUID))) ;; Random user

(def ws-connection (sente/make-channel-socket! {:user-id-fn get-user-id}))
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      ws-connection]
  (def ring-ws-post ajax-post-fn)
  (def ring-ws-handoff ajax-get-or-ws-handshake-fn)
  (def receive-channel ch-recv)
  (def channel-send! send-fn)
  (def connected-uids connected-uids))

(defn gentabledata [len]
  (reduce #(conj %1 [%2 %2 0])
          []
          (map #(* % 5) (range 100 (+ 100 len)))))

;; Table data
(def tableconfig (atom {
                         :colHeaders ["MD" "TVD" "Deviation"]
                         :data        (gentabledata 15)
                         :rowHeaders  false
                         :contextMenu true}))

;; Handle messages
(defn- handle-user-ident [db-connection data]
  @(d/transact db-connection
               [{:db/id #db/id[:db.part/user]
                  :user/name (:name data)}])
  (let [mydb (d/db db-connection)
        rawdata (q '[:find ?name
                     :where [_ :user/name ?name]]
                   mydb)
        usernames (vec (flatten (vec (into #{} rawdata))))]
    (doseq [uid (:any @connected-uids)]
       (channel-send! uid [:user/names usernames])
       (channel-send! uid [:db/table @tableconfig]))))

(defn- handle-user-change-MD [db-connnection changeData]
  (let [dataTable (get-in @tableconfig [:data])
        rowIdx (get changeData 0)
        colIdx (get changeData 1)
        newMD (Float/parseFloat (nth changeData 3))
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newMD)
        tmpDataTable1 (sort #(compare (get %1 0) (get %2 0)) tmpDataTable1)
        tmpDataTable2 (into [[0 0 0]] (drop-last tmpDataTable1))
        newDataTable (mapv (fn [in1 in2]
                             (let [md1 (get in1 0)
                                   tvd1 (get in1 1)
                                   md2 (get in2 0)
                                   tvd2 (get in2 1)
                                   dmd (- md1 md2)
                                   md3 md1
                                   tvd3 tvd1
                                   dev3 (* 180.0 (/ (Math/acos (/ (float (- tvd1 tvd2)) (float dmd))) Math/PI))]
                               [md3 tvd3 dev3]))
                           tmpDataTable1 tmpDataTable2)
        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
    (log/warn "handle-user-change-MD: " changeData)
    (reset! tableconfig newtableconfig)
    (doseq [uid (:any @connected-uids)]
      (channel-send! uid [:db/table @tableconfig]))))

(defn- handle-user-change-TVD [db-connection changeData]
  (let [dataTable (get-in @tableconfig [:data])
        rowIdx (get changeData 0)
        colIdx (get changeData 1)
        newTVD (Float/parseFloat (nth changeData 3))
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newTVD)
        tmpDataTable2 (into [[0 0 0]] (drop-last tmpDataTable1))
        newDataTable (mapv (fn [in1 in2]
                             (let [md1 (get in1 0)
                                   tvd1 (get in1 1)
                                   md2 (get in2 0)
                                   tvd2 (get in2 1)
                                   dmd (- md1 md2)
                                   md3 md1
                                   tvd3 tvd1
                                   dev3 (* 180.0 (/ (Math/acos (/ (float (- tvd1 tvd2)) (float dmd))) Math/PI))]
                               [md3 tvd3 dev3]))
                           tmpDataTable1 tmpDataTable2)
        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
    (log/warn "handle-user-change-TVD: " changeData)
    (reset! tableconfig newtableconfig)
    (doseq [uid (:any @connected-uids)]
      (channel-send! uid [:db/table @tableconfig]))))

(defn- handle-user-change-Deviation [db-connection changeData]
  (let [dataTable (get-in @tableconfig [:data])
        rowIdx (get changeData 0)
        colIdx (get changeData 1)
        newDeviation (Float/parseFloat (nth changeData 3))
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newDeviation)
        tmpDataTable2 (assoc-in tmpDataTable1 [0 1] (* (get-in tmpDataTable1 [0 0]) (Math/cos (* (/ (get-in tmpDataTable1 [0 2]) 180.0) Math/PI))))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     dev2 (get-in data [rowIdx 2])
                                     tvd2 (+ tvd1 (* (- md2 md1) (Math/cos (* (/ dev2 180.0) Math/PI))))]
                                 (assoc-in data [rowIdx 1] tvd2)))
                           tmpDataTable2
                           (range 1 (count tmpDataTable2)))
        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
    (log/warn "handle-user-change-Deviation: " changeData)
    (reset! tableconfig newtableconfig)
    (doseq [uid (:any @connected-uids)]
       (channel-send! uid [:db/table @tableconfig]))))

(defn- handle-user-default [db-connection changeData]
  (log/warn "handle-user-default: " changeData)
  (doseq [uid (:any @connected-uids)]
     (channel-send! uid [:db/table @tableconfig])))

(defn- handle-user-set-table-value [db-connection changeData]
  (let [column (get changeData 1)]
    (log/warn "column: " column)
    (cond
      (= 0 column) (handle-user-change-MD db-connection changeData)
      (= 1 column) (handle-user-change-TVD db-connection changeData)
      (= 2 column) (handle-user-change-Deviation db-connection changeData)
      :else (handle-user-default db-connection changeData))))

(defn- ws-msg-handler [db-connection]
  (fn [{:keys [event] :as msg} _]
    (let [[id data :as ev] event]
      (case id
        :user/ident (handle-user-ident db-connection data)
        :user/set-table-value (handle-user-set-table-value db-connection data)
        (log/warn "Unmatched event: " id)))))

(defn ws-message-router [db-connection]
  (sente/start-chsk-router-loop! (ws-msg-handler db-connection) 
                                 receive-channel))

;(defn- read-changes [{:keys [db-after tx-data] :as report}]
;  (q '[:find ?name
;       :in $ [[?e ?a ?v]]
;       :where [_ :user/name ?name]]
;     db-after
;     tx-data))

;; Handle Change
;(defn handle-change [change-queue]
;  (let [report (.take change-queue)
;        rawdata (read-changes report)
;        [[_ ?e ?v]] (:tx-data report)
;        changes (vec (flatten (vec (into #{} rawdata))))]
;    (log/warn "report: " report)
;    (log/warn "_ : " _)
;    (log/warn "?e: " ?e)
;    (log/warn "?v: " ?v)
;    (log/warn "rawdata: " rawdata)
;    (log/warn "changes: " changes)
;    (log/warn "connected-uids: " @connected-uids)
;    (doseq [uid (:any @connected-uids)]
;      (channel-send! uid [:room/join changes]))))

(defn change-monitor [change-queue]
  (log/info "starting monitor")
  ;(while true
  ;  (log/info "monitor loop"))
    ;(handle-change change-queue))
  (log/info "monitoring complete"))
