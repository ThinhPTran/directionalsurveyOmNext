(ns directionalsurvey.system
  (:require [taoensso.sente :as sente]
            [clojure.tools.logging :as log]
            [datomic.api :as d :refer [db q]]))

(defn now [] (new java.util.Date))

;;; query
;(->>
;  (d/q '[:find [(pull ?a [:name :order]) ...]
;         :in   $ ?e
;         :where [?e :aliases ?a]
;         db eid])
;  (sort-by :order)
;  (map :name))

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
                 ;:user/uid rcv-chan}])
  (let [mydb (d/db db-connection)
        rawdata (q '[:find [(pull ?e [:user/name]) ...]
                     :where [?e :user/name]]
                   mydb)
        usernames (mapv (fn [in]
                         (:user/name in)) rawdata)]
    ;(log/warn "rawdata: " usernames)
    (doseq [uid (:any @connected-uids)]
       (channel-send! uid [:user/names usernames])
       (channel-send! uid [:db/table @tableconfig]))))

;(defn- handle-user-change-MD [db-connection user changeData]
;  (let [dataTable (get-in @tableconfig [:data])
;        rowIdx (get changeData 0)
;        colIdx (get changeData 1)
;        newMD (Double/parseDouble (nth changeData 3))
;        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newMD)
;        tmpDataTable1 (sort #(compare (get %1 0) (get %2 0)) tmpDataTable1)
;        tmpDataTable2 (into [[0 0 0]] (drop-last tmpDataTable1))
;        newDataTable (mapv (fn [in1 in2]
;                             (let [md1 (get in1 0)
;                                   tvd1 (get in1 1)
;                                   md2 (get in2 0)
;                                   tvd2 (get in2 1)
;                                   dmd (- md1 md2)
;                                   md3 md1
;                                   tvd3 tvd1
;                                   dev3 (* 180.0 (/ (Math/acos (/ (float (- tvd1 tvd2)) (float dmd))) Math/PI))]
;                               [md3 tvd3 dev3]))
;                           tmpDataTable1 tmpDataTable2)
;        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
;    @(d/transact db-connection
;                 [{:db/id #db/id[:db.part/user]
;                   :action/user     user
;                   :action/row      rowIdx
;                   :action/column   colIdx
;                   :action/newval   newMD
;                   :action/instant  (now)}])
;    (log/warn "handle-user-change-MD: " changeData)
;    (reset! tableconfig newtableconfig)
;    (doseq [uid (:any @connected-uids)]
;      (channel-send! uid [:db/table @tableconfig]))))
;
;(defn- handle-user-change-TVD [db-connection user changeData]
;  (let [dataTable (get-in @tableconfig [:data])
;        rowIdx (get changeData 0)
;        colIdx (get changeData 1)
;        newTVD (Double/parseDouble (nth changeData 3))
;        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newTVD)
;        tmpDataTable2 (into [[0 0 0]] (drop-last tmpDataTable1))
;        newDataTable (mapv (fn [in1 in2]
;                             (let [md1 (get in1 0)
;                                   tvd1 (get in1 1)
;                                   md2 (get in2 0)
;                                   tvd2 (get in2 1)
;                                   dmd (- md1 md2)
;                                   md3 md1
;                                   tvd3 tvd1
;                                   dev3 (* 180.0 (/ (Math/acos (/ (float (- tvd1 tvd2)) (float dmd))) Math/PI))]
;                               [md3 tvd3 dev3]))
;                           tmpDataTable1 tmpDataTable2)
;        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
;    @(d/transact db-connection
;                 [{:db/id #db/id[:db.part/user]
;                   :action/user     user
;                   :action/row      rowIdx
;                   :action/column   colIdx
;                   :action/newval   newTVD
;                   :action/instant  (now)}])
;    (log/warn "handle-user-change-TVD: " changeData)
;    (reset! tableconfig newtableconfig)
;    (doseq [uid (:any @connected-uids)]
;      (channel-send! uid [:db/table @tableconfig]))))
;
;(defn- handle-user-change-Deviation [db-connection user changeData]
;  (let [dataTable (get-in @tableconfig [:data])
;        rowIdx (get changeData 0)
;        colIdx (get changeData 1)
;        newDeviation (Double/parseDouble (nth changeData 3))
;        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newDeviation)
;        tmpDataTable2 (assoc-in tmpDataTable1 [0 1] (* (get-in tmpDataTable1 [0 0]) (Math/cos (* (/ (get-in tmpDataTable1 [0 2]) 180.0) Math/PI))))
;        newDataTable (reduce (fn [data rowIdx]
;                               (let [md1 (get-in data [(- rowIdx 1) 0])
;                                     md2 (get-in data [rowIdx 0])
;                                     tvd1 (get-in data [(- rowIdx 1) 1])
;                                     dev2 (get-in data [rowIdx 2])
;                                     tvd2 (+ tvd1 (* (- md2 md1) (Math/cos (* (/ dev2 180.0) Math/PI))))]
;                                 (assoc-in data [rowIdx 1] tvd2)))
;                           tmpDataTable2
;                           (range 1 (count tmpDataTable2)))
;        newtableconfig (assoc-in @tableconfig [:data] newDataTable)]
;    @(d/transact db-connection
;                 [{:db/id #db/id[:db.part/user]
;                   :action/user     user
;                   :action/row      rowIdx
;                   :action/column   colIdx
;                   :action/newval   newDeviation
;                   :action/instant  (now)}])
;    (log/warn "handle-user-change-Deviation: " changeData)
;    (reset! tableconfig newtableconfig)
;    (doseq [uid (:any @connected-uids)]
;       (channel-send! uid [:db/table @tableconfig]))))
;
;(defn- handle-user-default [db-connection changeData]
;  (log/warn "handle-user-default: " changeData)
;  (doseq [uid (:any @connected-uids)]
;     (channel-send! uid [:db/table @tableconfig])))

(defn- handle-user-set-table-value [db-connection data]
  (let [username (:username data)
        changeData (:changeData data)
        rowIdx (get changeData 0)
        colIdx (get changeData 1)
        newVal (Double/parseDouble (nth changeData 3))]
    (log/warn "user: " username " rowIdx: " rowIdx " colIdx: " colIdx " newVal: " newVal)
    ;(cond
    ;  (= 0 column) (handle-user-change-MD db-connection username changeData)
    ;  (= 1 column) (handle-user-change-TVD db-connection username changeData)
    ;  (= 2 column) (handle-user-change-Deviation db-connection username changeData)
    ;  :else (handle-user-default db-connection changeData))
    @(d/transact db-connection
                     [{:db/id #db/id[:db.part/user]
                       :action/user     username
                       :action/row      rowIdx
                       :action/column   colIdx
                       :action/newval   newVal
                       :action/instant  (now)}])
    (let [mydb (d/db db-connection)
          result (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                      :where [?e :action/user]]
                     mydb)]
      (log/warn "raw actions: " result)
      (doseq [uid (:any @connected-uids)]
        (channel-send! uid [:user/listactions {:result result}])))))

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

(defn change-monitor [change-queue]
  (log/info "starting monitor")
  ;(while true
  ;  (log/info "monitor loop"))
    ;(handle-change change-queue))
  (log/info "monitoring complete"))
