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
      (.log js/console (str "Logging in with user: " name)))))
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
  (.log js/console "set-table-value: " changeDatas)
  (doseq [changeData changeDatas]
    (send-channel! [:user/set-table-value {:username (:user/name @mydb/local-login)
                                           :changeData changeData}])))

; handle application-specific events
(defn- app-message-received [[msgType data]]
  (case msgType
    :user/names (swap! mydb/global-users assoc :user/names data)
    :db/table (do
                (swap! mydb/global-states assoc :tableconfig data)
                (swap! mydb/local-states assoc :tableconfig data))
    (.log js/console "Unmatched application event")))

; handle websocket-connection-specific events
(defn- channel-state-message-received [state]
  (if (:first-open? state)
    (let [name (rand-nth possible-usernames)]
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