(ns directionalsurvey.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [directionalsurvey.config :as config]
            [directionalsurvey.events :as events]
            [directionalsurvey.db :as mydb]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

;; User names
(defui UserItems
  Object
  (render [this]
    (let [{:keys [name]} (om/props this)]
      (dom/li nil (str name)))))

(def ui-useritems (om/factory UserItems {:keyfn :name}))

(defui UserNames
  Object
  (render [this]
    (let [{:keys [user/names]} (om/props this)]
      (dom/div nil
               (dom/h2 nil "User names: ")
               ;(dom/div nil (str (om/props this)))
               (apply dom/ul nil
                            (map #(ui-useritems {:react-key %
                                                 :name %})
                                 names))))))

(def usernamesreconciler
  (om/reconciler {:state mydb/global-users}))

(om/add-root! usernamesreconciler
              UserNames (gdom/getElement "usernames"))

;; Counter
(defui Counter
  Object
  (render [this]
    (let [{:keys [count]} (om/props this)]
      (dom/div nil
               (dom/h2 nil (str "Count: " count))
               (dom/button
                 #js {:onClick
                      (fn [e]
                        (swap! mydb/local-count update-in [:count] inc))}
                 "Click me!")))))

(def counterreconciler
  (om/reconciler {:state mydb/local-count}))

(om/add-root! counterreconciler
              Counter (gdom/getElement "counter"))

;;; Handsontable

(defn mytableread
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mytablemutate om/dispatch)

(defmethod mytablemutate `settablevalue
  [{:keys [state] :as env} key {:keys [changeData]}]
  {:action (fn []
             (.log js/console "mytablemutate: " changeData))})

(defui MyTable
  static om/IQuery
  (query [this]
    [:tableconfig])
  Object
  (render [this]
    (dom/div
      #js {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}}))
  (componentDidMount [this]
    (let [{:keys [tableconfig]} (om/props this)]
      (swap! mydb/staticstates
             assoc
             :globaltable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            (.log js/console "change something!!!")
                                                                            (.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeData ~changeData})])))))))))

  (componentDidUpdate [this prev-props new-props]
    (let [{:keys [tableconfig]} (om/props this)
          table (:globaltable @mydb/staticstates)]
      (.log js/console "componentDidUpdate")
      (.destroy table)
      (swap! mydb/staticstates
             assoc
             :globaltable (js/Handsontable (dom/node this) (clj->js
                                                             (assoc-in tableconfig
                                                                       [:afterChange]
                                                                       #(do
                                                                          (let [changeData (js->clj %)]
                                                                            (.log js/console "change something!!!")
                                                                            (.log js/console "changeData: " changeData)
                                                                            (om/transact! this `[(settablevalue {:changeData ~changeData})]))))))))))
(def mytablereconciler
  (om/reconciler {:state mydb/global-states
                  :parser (om/parser {:read mytableread :mutate mytablemutate})}))

(om/add-root! mytablereconciler
              MyTable (gdom/getElement "mytable"))

(defn ^:export init []
  (dev-setup))