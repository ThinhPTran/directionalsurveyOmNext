(ns directionalsurvey.db)


(def init-tableconfig {:colHeaders ["MD" "TVD" "Deviation"]
                       :data        [[0 0 0]
                                     [0 0 0]
                                     [0 0 0]]
                       :rowHeaders  false
                       :contextMenu true})

(def globalconfig
  (atom {:tableconfig init-tableconfig}))

(def global-users
  (atom {:user/names ["No users"]}))

(def local-count
  (atom {:count 0}))

(def local-login
  (atom {:user/name nil
         :input-text "sample input"}))

(def global-states
  (atom {:name "Global cummulative states"
         :totalactions 0
         :currentpick 0
         :tableconfig init-tableconfig
         :totallistactions ["No actions"]
         :listactions ["No actions"]}))

(def local-states
  (atom {:name "Local user states"
         :tableconfig init-tableconfig
         :listactions ["No actions"]}))