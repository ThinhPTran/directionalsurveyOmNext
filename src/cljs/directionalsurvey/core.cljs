(ns directionalsurvey.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [directionalsurvey.config :as config]
            [directionalsurvey.events :as events]
            [directionalsurvey.utils :as utils]
            [directionalsurvey.db :as mydb]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

;; User names
(defn usernamesread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmulti usernamesmutate om/dispatch)

(defmethod usernamesmutate `get-usernames
  [{:keys [state] :as env} key params]
  {:action
   (fn []
     (events/get-usernames))})

(defui UserItems
  Object
  (render [this]
    (let [{:keys [name]} (om/props this)]
      (dom/li nil (str name)))))

(def ui-useritems (om/factory UserItems {:keyfn :name}))

(defui UserNames
  static om/IQuery
  (query [_]
    [:user/names])
  Object
  (render [this]
    (let [{:keys [user/names]} (om/props this)]
      (dom/div nil
               (dom/h2 nil "User names: ")
               (dom/button
                 #js {:id "btn-login"
                      :type "button"
                      :onClick (fn [_]
                                 (om/transact! this `[(get-usernames)]))}
                 "Get usernames")
               (apply dom/ul nil
                            (map #(ui-useritems {:react-key %
                                                 :name %})
                                 names))))))

(def usernamesreconciler
  (om/reconciler {:state mydb/global-users
                  :parser (om/parser {:read usernamesread :mutate usernamesmutate})}))

(om/add-root! usernamesreconciler
              UserNames (gdom/getElement "usernames"))

;; Slider
(defn mysliderread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmulti myslidermutate om/dispatch)

(defmethod myslidermutate `view-history
  [{:keys [state] :as env} key {:keys [currentpick]}]
  {:action
   (fn []
     (.log js/console "Current Pick: " currentpick)
     (events/set-history-point currentpick))})

(defui MySlider
  static om/IQuery
  (query [this]
    [:totalactions :currentpick :totallistactions])
  Object
  (render [this]
    (let [{:keys [totalactions currentpick totallistactions]} (om/props this)
          username (if (= currentpick 0) "No Information" (:action/user (get totallistactions (- currentpick 1))))
          instant (if (= currentpick 0) "No Information" (:action/instant (get totallistactions (- currentpick 1))))]
      (dom/div nil
               (dom/input #js {:id "myrange"
                               :type "range"
                               :min 0
                               :max totalactions
                               :value currentpick
                               :step 1
                               :onChange (fn [_]
                                           (let
                                             [v (.-value (gdom/getElement "myrange"))]
                                             (om/transact! this `[(view-history {:currentpick ~v})])))})
               ;(dom/div nil (str "Totallistactions: " totallistactions))
               ;(dom/div nil (str "Totalactions: " totalactions))
               ;(dom/div nil (str "Current pick: " currentpick))
               (dom/div nil (str "Username: " username))
               (dom/div nil (str "at: " instant))))))

  ;(componentDidMount [this]
  ;  (let [{:keys [totalactions currentpick]} (om/props this)]
  ;    (js/rangeslider (js/$ (dom/node this))))))

(def sliderreconciler
  (om/reconciler {:state mydb/global-states
                  :parser (om/parser {:read mysliderread :mutate myslidermutate})}))

(om/add-root! sliderreconciler
              MySlider (gdom/getElement "actionslider"))

;; Login form
(defn loginread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmulti loginmutate om/dispatch)

(defmethod loginmutate `update-text
  [{:keys [state] :as env} key {:keys [mytext]}]
  {:action
    (fn []
      (.log js/console mytext)
      (swap! state assoc :input-text mytext))})

(defmethod loginmutate `user-login
  [{:keys [state] :as env} key {:keys [name]}]
  {:action
   (fn []
     (events/user-login name))})

(defui LoginForm
  static om/IQuery
  (query [_]
    [:input-text :user/name])
  Object
  (render [this]
    (let [{:keys [input-text user/name]} (om/props this)]
      (dom/div nil
               (dom/input
                 #js {:id "my-input-box"
                      :type "text"
                      :value input-text
                      :onChange (fn [_]
                                   (let [v (.-value (gdom/getElement "my-input-box"))]
                                     (.log js/console "change something!!!")
                                     (om/transact! this `[(update-text {:mytext ~v})])))})
               (dom/button
                 #js {:id "btn-login"
                      :type "button"
                      :onClick (fn [_]
                                 (om/transact! this `[(user-login {:name ~input-text})]))}
                 "Secure login!")
               (dom/div nil (str "input text: " input-text))
               (dom/div nil (str "user name: " name))))))

(def loginreconciler
  (om/reconciler {:state mydb/local-login
                  :parser (om/parser {:read loginread :mutate loginmutate})}))

(om/add-root! loginreconciler
              LoginForm (gdom/getElement "loginform"))

;;; Handsontable
(defn mytableread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mytablemutate om/dispatch)

(defmethod mytablemutate `settablevalue
  [{:keys [state] :as env} key {:keys [changeDatas]}]
  {:action (fn []
             ;(.log js/console "mytablemutate: " changeDatas)
             (events/set-action changeDatas))})

;(defui MyGlobalTable
(defui MyGlobalTable
  static om/IQuery
  (query [this]
    [:tableconfig])
  Object
  (render [this]
    (let [{:keys [tableconfig]} (om/props this)
          table (atom {:table nil})]
      (dom/div
        #js {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}
             :ref (fn [mydiv]
                    (if (some? mydiv)
                       (swap! table assoc :table (js/Handsontable mydiv (clj->js
                                                                          (assoc-in tableconfig
                                                                                    [:afterChange]
                                                                                    #(do
                                                                                       (let [changeData (js->clj %)]
                                                                                         (om/transact! this `[(settablevalue {:changeDatas ~changeData})])))))))
                       (let [mytable (:table @table)]
                         (if (some? mytable)
                           (do
                             (.destroy mytable)
                             (swap! table assoc :table nil))))))}))))




(def myglobaltablereconciler
  (om/reconciler {:state mydb/global-states
                  :parser (om/parser {:read mytableread :mutate mytablemutate})}))

(om/add-root! myglobaltablereconciler
              MyGlobalTable (gdom/getElement "myglobaltable"))

;;; Highchart
(defui MyGlobalChart
  Object
  (render [this]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)
          chart (atom {:chart nil})]
      (dom/div
        #js {:style {:height "100%" :width "100%" :position "relative"}
             :ref (fn [mydiv]
                    (if (some? mydiv)
                      (swap! chart assoc :chart (js/Highcharts.Chart. mydiv (clj->js @my-chart-config)))
                      (let [mychart (:chart @chart)]
                        (if (some? mychart)
                          (do
                            (.destroy mychart)
                            (swap! chart :chart nil))))))}))))

(def myglobalchartreconciler
  (om/reconciler {:state mydb/global-states}))

(om/add-root! myglobalchartreconciler
              MyGlobalChart (gdom/getElement "myglobalchart"))

;; local transactions
(defui LocalTransactItem
  Object
  (render [this]
    (let [{:keys [name instant]} (om/props this)]
      (dom/li nil (str name " changed at " instant)))))

(def ui-localtransactitems (om/factory LocalTransactItem {:keyfn :instant}))

(defui LocalTransacts
  Object
  (render [this]
    (let [{:keys [listactions]} (om/props this)]
      (dom/div nil
               (dom/h2 nil "Local actions: ")
               ;(dom/div nil (str (om/props this)))
               (apply dom/ul nil
                      (map #(ui-localtransactitems {:react-key (:inst %)
                                                    :name (:user %)
                                                    :instant (:inst %)})
                           listactions))))))

(def localtransactionreconciler
  (om/reconciler {:state mydb/local-states}))

(om/add-root! localtransactionreconciler
              LocalTransacts (gdom/getElement "localtransaction"))


;;;; Handsontable
(defn mylocaltableread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mylocaltablemutate om/dispatch)

(defmethod mylocaltablemutate `settablevalue
  [{:keys [state] :as env} key {:keys [changeDatas]}]
  {:action (fn []
             ;(.log js/console "mytablemutate: " changeDatas)
             (events/set-action changeDatas))})

(defui MyLocalTable
  static om/IQuery
  (query [this]
    [:tableconfig])
  Object
  (render [this]
    (let [{:keys [tableconfig]} (om/props this)
          table (atom {:table nil})]
      (dom/div
        #js {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}
             :ref (fn [mydiv]
                    (if (some? mydiv)
                      (swap! table assoc :table (js/Handsontable mydiv (clj->js
                                                                         (assoc-in tableconfig
                                                                                   [:afterChange]
                                                                                   #(do
                                                                                      (let [changeData (js->clj %)]
                                                                                        (om/transact! this `[(settablevalue {:changeDatas ~changeData})])))))))
                      (let [mytable (:table @table)]
                        (if (some? mytable)
                          (do
                            (.destroy mytable)
                            (swap! table assoc :table nil))))))}))))

(def mylocaltablereconciler
  (om/reconciler {:state mydb/local-states
                  :parser (om/parser {:read mylocaltableread :mutate mylocaltablemutate})}))

(om/add-root! mylocaltablereconciler
              MyLocalTable (gdom/getElement "mylocaltable"))

;;; Highchart
(defui MyLocalChart
  Object
  (render [this]
    (let [{:keys [tableconfig]} (om/props this)
          my-chart-config (utils/gen-chart-config-handson tableconfig)
          chart (atom {:chart nil})]
      (dom/div
        #js {:style {:height "100%" :width "100%" :position "relative"}
             :ref (fn [mydiv]
                    (if (some? mydiv)
                      (swap! chart assoc :chart (js/Highcharts.Chart. mydiv (clj->js @my-chart-config)))
                      (let [mychart (:chart @chart)]
                        (if (some? mychart)
                          (do
                            (.destroy mychart)
                            (swap! chart :chart nil))))))}))))

(def mylocalchartreconciler
  (om/reconciler {:state mydb/local-states}))

(om/add-root! mylocalchartreconciler
              MyLocalChart (gdom/getElement "mylocalchart"))

;; Cummulative transactions
(defn cumtransactionread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti cumtransactionmutate om/dispatch)

(defmethod cumtransactionmutate `get-cum-actions
  [{:keys [state] :as env} key params]
  {:action (fn []
             (events/get-cum-actions))})

(defui CumTransactItem
  Object
  (render [this]
    (let [{:keys [name instant]} (om/props this)]
      (when (and name instant)
        (dom/li nil (str name " changed at " instant))))))

(def ui-cumtransactitems (om/factory CumTransactItem {:keyfn :instant}))

(defui CumTransacts
  static om/IQuery
  (query [this]
    [:listactions])
  Object
  (render [this]
    (let [{:keys [listactions]} (om/props this)]
      (dom/div nil
               (dom/h2 nil "Cummulative actions: ")
               (dom/button
                 #js {:id "btn-login"
                      :type "button"
                      :onClick (fn [_]
                                 (om/transact! this `[(get-cum-actions)]))}
                 "Get Cum Actions")
               (apply dom/ul nil
                      (map #(ui-cumtransactitems {:react-key (:inst %)
                                                  :name (:user %)
                                                  :instant (:inst %)})
                           listactions))))))

(def cumtransactionreconciler
  (om/reconciler {:state mydb/global-states
                  :parser (om/parser {:read cumtransactionread :mutate cumtransactionmutate})}))

(om/add-root! cumtransactionreconciler
              CumTransacts (gdom/getElement "cumtransaction"))


(defn ^:export init []
  (dev-setup))