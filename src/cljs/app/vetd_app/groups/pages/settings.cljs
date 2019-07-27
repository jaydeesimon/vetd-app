(ns vetd-app.groups.pages.settings
  (:require [vetd-app.ui :as ui]
            [vetd-app.common.components :as cc]
            [vetd-app.buyers.components :as bc]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [clojure.string :as s]))

(rf/reg-event-fx
 :g/nav-settings
 (constantly
  {:nav {:path "/c/settings"}
   :analytics/track {:event "Navigate"
                     :props {:category "Navigation"
                             :label "Groups Settings"}}}))

(rf/reg-event-fx
 :g/route-settings
 (fn [{:keys [db]}]
   {:db (assoc db
               :page :g/settings
               :page-params {:fields-editing #{}})
    :analytics/page {:name "Groups Settings"}}))

(rf/reg-event-fx
 :g/add-orgs-to-group
 (fn [{:keys [db]} [_ group-id org-ids]]
   {:ws-send {:payload {:cmd :g/add-orgs-to-group
                        :return {:handler :g/add-orgs-to-group-return
                                 :org-ids org-ids}
                        :group-id group-id
                        :org-ids org-ids}}
    :analytics/track {:event "Add Organization"
                      :props {:category "Community"}}}))

(rf/reg-event-fx
 :g/add-orgs-to-group-return
 (fn [{:keys [db]} [_ _ {{:keys [org-ids]} :return}]]
   {:toast {:type "success"
            :title (str "Organization" (when (> (count org-ids) 1) "s") " added to your community!")}
    :dispatch [:stop-edit-field "add-orgs-to-group"]}))

;; remove an org from a group
(rf/reg-event-fx
 :g/remove-org
 (fn [{:keys [db]} [_ group-id org-id]]
   {:ws-send {:payload {:cmd :g/remove-org
                        :return {:handler :g/remove-org-return}
                        :group-id group-id
                        :org-id org-id}}
    :analytics/track {:event "Remove Organization"
                      :props {:category "Community"}}}))

(rf/reg-event-fx
 :g/remove-org-return
 (fn [{:keys [db]}]
   {:toast {:type "success"
            :title "Organization removed from your community."}}))

(defn c-add-orgs-form [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])
        bad-input& (rf/subscribe [:bad-input])
        value& (r/atom [])
        options& (r/atom []) ; options from search results + current values
        search-query& (r/atom "")
        orgs->options (fn [orgs]
                        (for [{:keys [id oname]} orgs]
                          {:key id
                           :text oname
                           :value id}))]
    (fn [group]
      (when (@fields-editing& "add-orgs-to-group")
        (let [orgs& (rf/subscribe
                     [:gql/q
                      {:queries
                       [[:orgs {:_where {:oname {:_ilike (str "%" @search-query& "%")}}
                                :_limit 25
                                :_order_by {:oname :asc}}
                         [:id :oname]]]}])
              org-ids-already-in-group (set (map :id (:orgs group)))
              _ (when-not (= :loading @orgs&)
                  (let [options (->> @orgs&
                                     :orgs
                                     orgs->options ; now we have options from gql sub
                                     ;; (this dumbly actually keeps everything, but that seems fine)
                                     (concat @options&) ; keep options for the current values
                                     distinct
                                     (remove (comp (partial contains? org-ids-already-in-group) :value)))]
                    (when-not (= @options& options)
                      (reset! options& options))))]
          [:> ui/Form {:as "div"
                       :class "popup-dropdown-form"} ;; popup is a misnomer here
           [:> ui/FormField {:error (= @bad-input& :add-orgs-to-group)
                             :style {:padding-top 7
                                     :width "100%"}
                             ;; this class combo w/ width 100% is a hack
                             :class "ui action input"}
            [:> ui/Dropdown {:loading (= :loading @orgs&)
                             :options @options&
                             :placeholder "Search organizations..."
                             :search true
                             :selection true
                             :multiple true
                             ;; :auto-focus true ;; TODO this doesn't work
                             :selectOnBlur false
                             :selectOnNavigation true
                             :closeOnChange true
                             :allowAdditions false ;; TODO this should be changed to true when we allow invites of new orgs
                             ;; :additionLabel "Hit 'Enter' to Add "
                             ;; :onAddItem (fn [_ this]
                             ;;              (->> this
                             ;;                   .-value
                             ;;                   vector
                             ;;                   ui/as-dropdown-options
                             ;;                   (swap! options& concat)))
                             :onSearchChange (fn [_ this] (reset! search-query& (aget this "searchQuery")))
                             :onChange (fn [_ this] (reset! value& (.-value this)))}]
            [:> ui/Button
             {:color "teal"
              :disabled (empty? @value&)
              :on-click #(rf/dispatch [:g/add-orgs-to-group (:id group) (js->clj @value&)])}
             "Add"]]])))))

(defn c-org
  [org group]
  (let [popup-open? (r/atom false)]
    (fn [{:keys [id idstr oname memberships] :as org}
         {:keys [gname] :as group}]
      (let [num-members (count memberships)]
        [cc/c-field {:label [:<>
                             [:> ui/Popup
                              {:position "bottom right"
                               :on "click"
                               :open @popup-open?
                               :on-close #(reset! popup-open? false)
                               :content (r/as-element
                                         [:div
                                          [:h5 "Are you sure you want to remove " oname " from " gname "?"]
                                          [:> ui/ButtonGroup {:fluid true}
                                           [:> ui/Button {:on-click #(reset! popup-open? false)}
                                            "Cancel"]
                                           [:> ui/Button {:on-click (fn []
                                                                      (reset! popup-open? false)
                                                                      (rf/dispatch [:g/remove-org (:id group) id]))
                                                          :color "red"}
                                            "Remove"]]])
                               :trigger (r/as-element
                                         [:> ui/Label {:on-click #(swap! popup-open? not)
                                                       :as "a"
                                                       :style {:float "right"
                                                               :margin-top 5}}
                                          [:> ui/Icon {:name "remove"}]
                                          "Remove"])}]
                             oname]
                     :value [:<> (str num-members " member" (when-not (= num-members 1) "s") " ")
                             [:> ui/Popup
                              {:position "bottom left"
                               :wide "very"
                               :content (let [max-members-show 15]
                                          (str (s/join ", " (->> memberships
                                                                 (map (comp :uname :user))
                                                                 (take max-members-show)))
                                               (when (> num-members max-members-show)
                                                 (str " and " (- num-members max-members-show) " more."))))
                               :trigger (r/as-element
                                         [:> ui/Icon {:name "question circle"}])}]]}]))))

(defn c-add-discount-form [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])
        bad-input& (rf/subscribe [:bad-input])
        product& (r/atom nil)
        details& (r/atom "")
        options& (r/atom []) ; options from search results + current values
        search-query& (r/atom "")
        products->options (fn [products]
                            (for [{:keys [id pname]} products]
                              {:key id
                               :text pname
                               :value id}))]
    (fn [group]
      (when (@fields-editing& "add-discount-to-group")
        (let [products& (rf/subscribe
                         [:gql/q
                          {:queries
                           [[:products {:_where {:pname {:_ilike (str "%" @search-query& "%")}}
                                        :_limit 100
                                        :_order_by {:pname :asc}}
                             [:id :pname]]]}])
              _ (when-not (= :loading @products&)
                  (let [options (->> @products&
                                     :products
                                     products->options ; now we have options from gql sub
                                     ;; (this dumbly actually keeps everything, but that seems fine)
                                     (concat @options&) ; keep options for the current values
                                     distinct)]
                    (when-not (= @options& options)
                      (reset! options& options))))]
          [:> ui/Form
           [:> ui/FormField {:error (= @bad-input& :add-discount-to-group.product-id)
                             :style {:padding-top 7}}
            [:> ui/Dropdown {:loading (= :loading @products&)
                             :options @options&
                             :placeholder "Search products..."
                             :search true
                             :selection true
                             :multiple false
                             ;; :auto-focus true ;; TODO this doesn't work
                             :selectOnBlur false
                             :selectOnNavigation true
                             :closeOnChange true
                             :onSearchChange (fn [_ this] (reset! search-query& (aget this "searchQuery")))
                             :onChange (fn [_ this] (reset! product& (.-value this)))}]]
           [:> ui/FormField {:error (= @bad-input& :add-discount-to-group.details)}
            [:> ui/Input
             {:placeholder "Discount details..."
              :fluid true
              :on-change #(reset! details& (-> % .-target .-value))
              :action (r/as-element
                       [:> ui/Button {:on-click #(rf/dispatch [:g/add-discount-to-group
                                                               (:id group)
                                                               (js->clj @product&)
                                                               @details&])
                                      :disabled (nil? @product&) ; TODO that only works the first time... cancelling edit needs to reset product&
                                      :color "blue"}
                        "Add"])}]]])))))

(defn c-discount
  [{:keys [id idstr pname group-discount-descr vendor] :as discount}]
  [cc/c-field {:label [:<>
                       [:a.name {:on-click #(rf/dispatch [:b/nav-product-detail idstr])}
                        pname]
                       [:small " by " (:oname vendor)]]
               :value group-discount-descr}])

(defn c-group
  [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])]
    (fn [{:keys [id gname orgs discounts] :as group}]
      [:> ui/Grid {:stackable true}
       [:> ui/GridRow
        [:> ui/GridColumn {:computer 16 :mobile 16}
         [:h1 {:style {:text-align "center"}}
          gname]]]
       [:> ui/GridRow
        ;; Organizations
        [:> ui/GridColumn {:computer 8 :mobile 16}
         [bc/c-profile-segment
          {:title [:<>
                   (if (@fields-editing& "add-orgs-to-group")
                     [:> ui/Label {:on-click #(rf/dispatch
                                               [:stop-edit-field "add-orgs-to-group"])
                                   :as "a"
                                   :style {:float "right"}}
                      "Cancel"]
                     [:> ui/Label {:on-click #(rf/dispatch
                                               [:edit-field "add-orgs-to-group"])
                                   :as "a"
                                   :color "teal"
                                   :style {:float "right"}}
                      [:> ui/Icon {:name "add group"}]
                      "Add Organization"])
                   "Organizations"
                   [c-add-orgs-form group]]}
          (for [org orgs]
            ^{:key (:id org)}
            [c-org org group])]]
        ;; Discounts
        [:> ui/GridColumn {:computer 8 :mobile 16}
         [bc/c-profile-segment
          {:title [:<>
                   (if (@fields-editing& "add-discount-to-group")
                     [:> ui/Label {:on-click #(rf/dispatch
                                               [:stop-edit-field "add-discount-to-group"])
                                   :as "a"
                                   :style {:float "right"}}
                      "Cancel"]
                     [:> ui/Label {:on-click #(rf/dispatch
                                               [:edit-field "add-discount-to-group"])
                                   :as "a"
                                   :color "blue"
                                   :style {:float "right"}}
                      [:> ui/Icon {:name "dollar"}]
                      "Add Discount"])
                   "Discounts"
                   [c-add-discount-form group]]}
          (for [discount discounts]
            ^{:key (:id discount)}
            [c-discount discount])]]]])))

(defn c-groups
  [groups]
  [:div
   (for [group groups]
     ^{:key (:id group)}
     [c-group group])])

(defn c-page []
  (let [org-id& (rf/subscribe [:org-id])
        groups& (rf/subscribe [:gql/sub
                               {:queries
                                [[:groups {:admin-org-id @org-id&
                                           :deleted nil}
                                  [:id :gname
                                   [:orgs
                                    [:id :oname
                                     [:memberships
                                      [:id
                                       [:user
                                        [:id :uname]]]]]]
                                   [:discounts
                                    ;; i.e., product 'id' and product 'idstr'
                                    [:id :idstr :pname
                                     :group-discount-descr
                                     [:vendor
                                      [:id :oname]]]]]]]}])]
    (fn []
      (if (= :loading @groups&)
        [cc/c-loader]
        [c-groups (:groups @groups&)]))))

