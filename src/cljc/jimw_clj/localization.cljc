(ns jimwclj.localization)

(def defaults
  {:page-title "TIKO-MSG"
   :loading "Please wait while the app is loading"
   :tiko-msg "TIKO-MSG"
   :new-msg "New MSG"})

(defn tr
  [k]
  (or (get defaults k)
      (str "Not yet localized: " k)))
