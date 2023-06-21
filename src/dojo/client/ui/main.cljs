(ns dojo.client.ui.main
  (:require
    [clojure.string :as string]
    [re-frame.core :refer [dispatch subscribe]]
    [bloom.commons.fontawesome :as fa]
    [dojo.client.state :as state]
    [dojo.model :as model]))

(defn max-limit-preferences-view []
  [:div.max-limit-preferences
   [:label
    "Max pair per day"
    [:input {:type "number"
             :value @(subscribe [:user-profile-value :user/max-pair-per-day])
             :min 1
             :max 24
             :on-change (fn [e]
                           (dispatch [:set-user-value!
                                      :user/max-pair-per-day
                                      (js/parseInt (.. e -target -value) 10)]))}]]
   [:label
    "Max pair per week"
    [:input {:type "number"
             :value @(subscribe [:user-profile-value :user/max-pair-per-week])
             :min 1
             :max (* 24 7)
             :on-change (fn [e]
                           (dispatch [:set-user-value!
                                      :user/max-pair-per-week
                                      (js/parseInt (.. e -target -value) 10)]))}]]])

(defn next-day-of-week
  "Calculates next date with day of week as given"
  [now target-day-of-week]
  (let [target-day-of-week ({:monday 1
                             :tuesday 2
                             :wednesday 3
                             :thursday 4
                             :friday 5
                             :saturday 6
                             :sunday 0} target-day-of-week)
        now-day-of-week (.getDay now)
        ;; must be a nice way to do this with mod
        ;; (mod (- 7 now-day-of-week) 7)
        delta-days (get (zipmap (range 0 7)
                                (take 7 (drop (- 7 target-day-of-week)
                                              (cycle (range 7 0 -1)))))
                        now-day-of-week)
        new-date (doto (js/Date. (.valueOf now))
                   (.setDate (+ delta-days (.getDate now))))]
    new-date))

(defn add-days [day delta-days]
  (doto (js/Date. (.valueOf day))
    (.setDate (+ delta-days (.getDate day)))))

(defn format-date [date]
  (.format (js/Intl.DateTimeFormat. "en-US" #js {:weekday "long"
                                                 :month "short"
                                                 :day "numeric"})
           date))

(defn topics-view []
  [:div.topics-view
   [:h1 "Topics"]
   (let [user-topic-ids @(subscribe [:user-profile-value :user/topic-ids])]
    [:<>
     (when (and (empty? user-topic-ids)
                @(subscribe [:user-profile-value :user/pair-next-week?]))
      [:p.warning
       [fa/fa-exclamation-triangle-solid]
       "You need to select at least one topic to be matched with someone."])
     [:div.topics
      (for [topic (sort-by :topic/name @(subscribe [:topics]))
            :let [checked? (contains? user-topic-ids (:topic/id topic))]]
        ^{:key (:topic/id topic)}
        [:label.topic
         [:input {:type "checkbox"
                  :checked checked?
                  :on-change
                  (fn []
                    (if checked?
                      (dispatch [:remove-user-topic! (:topic/id topic)])
                      (dispatch [:add-user-topic! (:topic/id topic)])))}]
         [:span.name (:topic/name topic)] " "
         [:span.count (:topic/user-count topic)]])
      [:button
       {:on-click (fn [_]
                    (let [value (js/prompt "Enter a new topic:")]
                      (when (not (string/blank? value))
                        (dispatch [:new-topic! (string/trim value)]))))}
       "+ Add Topic"]]])])

(defn availability-view []
  (when-let [availability @(subscribe [:user-profile-value :user/availability])]
    [:table.availability
     [:thead
      [:tr
       [:th]
       (let [next-monday (next-day-of-week (js/Date.) :monday)]
         (for [[i day] (map-indexed (fn [i d] [i d]) model/days)]
          (let [[day-of-week date] (string/split (format-date (add-days next-monday i)) #",")]
           ^{:key day}
           [:th.day
            [:div.day-of-week day-of-week]
            [:div.date date]])))]]
     [:tbody
      (doall
       (for [hour model/hours]
         ^{:key hour}
         [:tr
          [:td.hour
           hour]
          (doall
           (for [day model/days]
             ^{:key day}
             [:td
              (let [value (availability [day hour])]
                [:button
                 {:class (case value
                           :preferred "preferred"
                           :available "available"
                           nil "empty")
                  :on-click (fn [_]
                              (dispatch [:set-availability!
                                         [day hour]
                                         (case value
                                           :preferred nil
                                           :available :preferred
                                           nil :available)]))}
                 [:div.wrapper
                  (case value
                    :preferred "P"
                    :available "A"
                    nil "")]])]))]))]]))

(defn opt-in-view []
  (let [checked? @(subscribe [:user-profile-value :user/pair-next-week?])]
   [:button.opt-in
    {:class (when checked? "active")
     :on-click (fn []
                 (dispatch
                  [:opt-in-for-pairing! (not checked?)]))}
    (if checked?
      [fa/fa-check-square-regular]
      [fa/fa-square-regular])
    "Pair next week?"]))

(defn name-view []
  [:label.name "Name "
   [:input {:type "text"
            :value @(subscribe [:user-profile-value :user/name])
            :on-change (fn [e]
                          (dispatch
                            [:set-user-value! :user/name (.. e -target -value)]))}]])

(defn time-zone-view []
  [:label.time-zone "Time Zone "
   [:input {:type "text"
            :disabled true
            :value @(subscribe [:user-profile-value :user/time-zone])}]
   [:button
    {:on-click (fn []
                  (dispatch
                    [:set-user-value! :user/time-zone (.. js/Intl DateTimeFormat resolvedOptions -timeZone)]))}
    "Update"]])

(defn ajax-status-view []
  [:div.ajax-status {:class (if (empty? @state/ajax-state) "normal" "loading")}
   (if (empty? @state/ajax-state)
     [fa/fa-check-circle-solid]
     [fa/fa-circle-notch-solid])])

(defn subscription-toggle-view []
  (if @(subscribe [:user-profile-value :user/subscribed?])
    [:button.unsubscribe
     {:on-click (fn []
                  (when (js/window.confirm "Are you sure you want to unsubscribe?")
                    (dispatch [:update-subscription! false])))}
     "Unsubscribe"]
    [:button.subscribe
     {:on-click (fn []
                  (dispatch [:update-subscription! true]))}
     "Re-Subscribe"]))

(defn format-date-2 [date]
  (.format (js/Intl.DateTimeFormat. "default" #js {:day "numeric"
                                                   :month "short"
                                                   :year "numeric"
                                                   :hour "numeric"})
           date))

(defn event-view [heading event]
 (let [guest-name (:user/name (:event/other-guest event))
       other-guest-flagged? (contains? (:event/flagged-guest-ids event)
                                       (:user/id (:event/other-guest event)))]
   [:tr.event {:class (if (< (js/Date.) (:event/at event))
                        "future"
                        "past")}
    [:th heading]
    [:td
     [:span.at (format-date-2 (:event/at event))]
     " with "
     [:span.other-guest (:user/name (:event/other-guest event))]]
    [:td
     [:div.actions
      [:a.link {:href (str "mailto:" (:user/email (:event/other-guest event)))}
       [fa/fa-envelope-solid]]
      [:a.link {:href (model/->jitsi-url event)}
       [fa/fa-video-solid]]
      [:button.flag
       {:class (when other-guest-flagged? "flagged")
        :on-click (fn []
                    (if other-guest-flagged?
                      (dispatch [:flag-event-guest! (:event/id event) false])
                      (when (js/confirm (str "Are you sure you want to report " guest-name " for not showing up?"))
                       (dispatch [:flag-event-guest! (:event/id event) true]))))}
       [fa/fa-flag-solid]]]]]))

(defn events-view []
  (let [events @(subscribe [:events])
        [upcoming-events past-events] (->> events
                                           (sort-by :event/at)
                                           reverse
                                           (split-with (fn [event]
                                                         (< (js/Date.) (:event/at event)))))]
   [:table.events
    [:tbody
     (for [[index event] (map-indexed vector upcoming-events)]
       ^{:key (:event/id event)}
       [event-view (when (= 0 index) "Upcoming Sessions") event])
     (for [[index event] (map-indexed vector past-events)]
       ^{:key (:event/id event)}
       [event-view (when (= 0 index) "Past Sessions") event])]]))

(defn header-view []
  [:div.header
   [:img.logomark
    {:src "/logomark.svg"
     :alt "Logo of Clojure Camp. A star constellation in the shape of alambda."}]
   [:div.gap]
   [:img.logotype
    {:src "/logotype.svg"
     :alt "Clojure Camp"}]
   [:div.gap]
   [:button.log-out
    {:on-click (fn []
                 (dispatch [:log-out!]))}
    "Log Out"]])

(defn main-view []
  [:div.main
   [ajax-status-view]
   [header-view]
   [:div.content
    [opt-in-view]
    [name-view]
    [topics-view]
    [max-limit-preferences-view]
    [time-zone-view]
    [availability-view]
    [events-view]
    [subscription-toggle-view]]])
