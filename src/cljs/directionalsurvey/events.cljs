(ns directionalsurvey.events
  (:require [taoensso.encore :as encore]
            [taoensso.sente :as sente :refer (cb-success?)]
            [directionalsurvey.db :as mydb]
            [clojure.string :as str]))

; generate a list of usernames
(def possible-usernames
  (let [first-names ["Happy" "Gleeful" "Joyful" "Cheerful" "Merry" "Jolly"]
        last-names ["Thinh" "Quang" "Dung" "Anh" "Minh" "Huy"]]
    (for [name1 first-names
          name2 last-names]
      (str name1 " " name2))))

; sente js setup
(def ws-connection (sente/make-channel-socket! "/channel" {:type :auto}))
(let [{:keys [ch-recv send-fn]}
      ws-connection]
  (def chsk            chsk)
  (def receive-channel ch-recv)
  (def send-channel!   send-fn))

;; User login
(defn user-login [name]
  (if (str/blank? name)
    (js/alert "Please enter a user name")
    (do
      (.log js/console (str "Logging in with user: " name))
      (swap! mydb/local-login assoc :user/name name)
      (send-channel! [:user/ident {:name name}]))))
      ;(encore/ajax-lite "/login"
      ;                 {:method :post
      ;                  :headers {:X-CSRF-Token (:csrf-token @receive-channel)}
      ;                  :params  {:user-id (str name)}}
      ;
      ;                 (fn [ajax-resp]
      ;                   (.log js/console (str "Ajax login response: %s" ajax-resp))
      ;                   (let [login-successful? true] ; Your logic here
      ;
      ;                     (if-not login-successful?
      ;                       (.log js/console "Login failed")
      ;                       (do
      ;                         (.log js/console "Login successful")
      ;                         (sente/chsk-reconnect! chsk)))))))))

;; Event handler definitions
(defn set-table-value [changeDatas]
  (if (some? changeDatas)
    (do
      (.log js/console "set-table-value: " changeDatas)
      (doseq [changeData changeDatas]
          (send-channel! [:user/set-table-value {:username (:user/name @mydb/local-login)
                                                 :changeData changeData}])))))

(defn set-history-point [idx]
  (send-channel! [:user/set-history-point {:idx (int idx)}]))

(defn handle-set-table [data]
  (swap! mydb/globalconfig assoc :tableconfig data)
  (swap! mydb/global-states assoc :tableconfig data)
  (swap! mydb/local-states assoc :tableconfig data))

(defn handle-user-change-MD [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:action/row action)
        colIdx (:action/column action)
        newMD (:action/newval action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newMD)
        tmpDataTable1 (vec (sort #(compare (get %1 0) (get %2 0)) tmpDataTable1))
        tmpDataTable2 (assoc-in tmpDataTable1 [0 2] (* 180.0
                                                       (/
                                                         (js/Math.acos
                                                           (/ (double (get-in tmpDataTable1 [0 1])) (double (get-in tmpDataTable1 [0 0]))))
                                                         js/Math.PI)))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     tvd2 (get-in data [rowIdx 1])
                                     dev3 (* 180.0
                                             (/
                                               (js/Math.acos (/ (double (- tvd1 tvd2)) (double (- md1 md2))))
                                               js/Math.PI))]
                                 (assoc-in data [rowIdx 2] dev3)))
                             tmpDataTable2
                             (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-user-change-TVD [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:action/row action)
        colIdx (:action/column action)
        newTVD (:action/newval action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newTVD)
        tmpDataTable2 (assoc-in tmpDataTable1 [0 2] (* 180.0
                                                       (/
                                                         (js/Math.acos
                                                           (/ (double (get-in tmpDataTable1 [0 1])) (double (get-in tmpDataTable1 [0 0]))))
                                                         js/Math.PI)))
        newDataTable (reduce (fn [data rowIdx]
                                (let [md1 (get-in data [(- rowIdx 1) 0])
                                      md2 (get-in data [rowIdx 0])
                                      tvd1 (get-in data [(- rowIdx 1) 1])
                                      tvd2 (get-in data [rowIdx 1])
                                      dev3 (* 180.0
                                             (/
                                               (js/Math.acos (/ (double (- tvd1 tvd2)) (double (- md1 md2))))
                                               js/Math.PI))]
                                  (assoc-in data [rowIdx 2] dev3)))
                             tmpDataTable2
                             (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-user-change-Deviation [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:action/row action)
        colIdx (:action/column action)
        newDeviation (:action/newval action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newDeviation)
        tmpDataTable2 (assoc-in tmpDataTable1 [0 1] (* (get-in tmpDataTable1 [0 0]) (Math/cos (* (/ (get-in tmpDataTable1 [0 2]) 180.0) Math/PI))))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     dev2 (get-in data [rowIdx 2])
                                     tvd2 (+ tvd1 (* (- md2 md1) (js/Math.cos (* (/ dev2 180.0) js/Math.PI))))]
                                 (assoc-in data [rowIdx 1] tvd2)))
                           tmpDataTable2
                           (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-table-actions [tableconfig action]
  (let [colIdx (:action/column action)]
    ;(.log js/console "action: " action)
    (cond
      (= 0 colIdx) (handle-user-change-MD tableconfig action)
      (= 1 colIdx) (handle-user-change-TVD tableconfig action)
      (= 2 colIdx) (handle-user-change-Deviation tableconfig action))))

(defn handle-global-table [data]
  (let [myinitconfig (:tableconfig @mydb/globalconfig)
        newtableconfig (reduce (fn [indata action]
                                 (handle-table-actions indata action)) myinitconfig data)]
    (swap! mydb/global-states assoc :tableconfig newtableconfig)))

(defn handle-local-table [data]
  (let [myinitconfig (:tableconfig @mydb/globalconfig)
        newtableconfig (reduce (fn [indata action]
                                 (handle-table-actions indata action)) myinitconfig data)]
    (swap! mydb/local-states assoc :tableconfig newtableconfig)))

(defn handle-listactions-received [data]
  (let [rawdata (:result data)
        totalcumactions (vec (sort #(compare (:action/instant %1) (:action/instant %2)) rawdata))
        username (:user/name @mydb/local-login)
        localactions (filterv #(= (:action/user %) username) totalcumactions)
        Naction (count totalcumactions)
        myinitconfig (:tableconfig @mydb/globalconfig)]
    (if (some? username)
      (do
        (.log js/console "User name: " username)
        (.log js/console "Cummulative actions: " totalcumactions)
        (.log js/console "Local actions: " localactions)
        (.log js/console "Naction: " Naction)
        (swap! mydb/global-states assoc :totallistactions totalcumactions)
        (swap! mydb/global-states assoc :listactions totalcumactions)
        (swap! mydb/local-states assoc :listactions localactions)
        (swap! mydb/global-states assoc :totalactions Naction)
        (swap! mydb/global-states assoc :currentpick Naction)
        (handle-global-table totalcumactions)
        (handle-local-table localactions))
      (do
        (.log js/console "Not signed in!!!")
        (swap! mydb/global-states assoc :tableconfig myinitconfig)
        (swap! mydb/local-states assoc :tableconfig myinitconfig)))))

(defn handle-set-history-point [data]
  (let [idx (:idx data)
        username (:user/name @mydb/local-login)
        tottalcumactions (:totallistactions @mydb/global-states)
        newtotalcumactions (subvec tottalcumactions 0 idx)
        newlocalactions (filterv #(= (:action/user %) username) newtotalcumactions)
        Naction idx]
    (.log js/console "User name: " username)
    (.log js/console "idx: " idx)
    (.log js/console "totalcumactions: " tottalcumactions)
    (.log js/console "newtotalcumactions: " newtotalcumactions)
    (.log js/console "newlocalcumactions: " newlocalactions)
    (swap! mydb/global-states assoc :listactions newtotalcumactions)
    (swap! mydb/local-states assoc :listactions newlocalactions)
    (swap! mydb/global-states assoc :currentpick Naction)
    (handle-global-table newtotalcumactions)
    (handle-local-table newlocalactions)))


; handle application-specific events
(defn- app-message-received [[msgType data]]
  (case msgType
    :user/names (swap! mydb/global-users assoc :user/names data)
    :db/table (handle-set-table data)
    :user/listactions (handle-listactions-received data)
    :user/set-history-point (handle-set-history-point data)
    (.log js/console "Unmatched application event")))

; handle websocket-connection-specific events
(defn- channel-state-message-received [state]
  (if (:first-open? state)
    (let [name nil];;(rand-nth possible-usernames)
      (swap! mydb/local-login assoc :user/name name)
      (send-channel! [:user/ident {:name name}]))))

; main router for websocket events
(defn- event-handler [[id data] _]
  (.log js/console "received message" data)
  (case id
    :chsk/state (channel-state-message-received data)
    :chsk/recv (app-message-received data)
    (.log js/console "Unmatched connection event")))

(sente/start-chsk-router-loop! event-handler receive-channel)