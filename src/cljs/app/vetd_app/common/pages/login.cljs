(ns vetd-app.common.pages.login
  (:require [vetd-app.ui :as ui]
            [vetd-app.common.components :as cc]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-com.core :as rc]))

(rf/reg-sub
 :login-failed?
 (fn [{:keys [login-failed?]} _] login-failed?))

(rf/reg-sub
 :login-loading?
 (fn [{:keys [login-loading?]} _] login-loading?))

(rf/reg-event-fx
 :route-login
 (fn [{:keys [db]}]
   {:db (assoc db :page :login)
    :analytics/page {:name "Login"}}))

(rf/reg-event-fx
 :nav-login
 (fn []
   {:nav {:path "/login"}}))

(rf/reg-event-fx
 :login
 (fn [{:keys [db]} [_ [email pwd]]]
   {:db (assoc db
               :login-failed? false
               :login-loading? true)
    :ws-send {:payload {:cmd :auth-by-creds
                        :return :login-result
                        :email email
                        :pwd pwd}}}))

(rf/reg-event-fx
 :login-result
 (fn [{:keys [db]} [_ {:keys [logged-in? user session-token memberships admin?]
                       :as results}]]
   (if logged-in?
     (let [org-id (-> memberships first :org-id)] ; TODO support users with multi-orgs
       {:db (assoc db
                   :login-failed? false
                   :logged-in? true
                   :user user
                   :session-token session-token
                   :memberships memberships
                   :active-memb-id (some-> memberships first :id)
                   :admin? admin?
                   :org-id org-id)
        :local-store {:session-token session-token}
        :cookies {:admin-token (when admin? [session-token {:max-age 60 :path "/"}])}
        :analytics/identify {:user-id (:id user)}
        :analytics/group {:group-id org-id}
        :dispatch-later [{:ms 100 :dispatch [:nav-home]}
                         ;; to prevent the login form from flashing briefly
                         {:ms 200 :dispatch [:hide-login-loading]}]})
     {:db (assoc db
                 :logged-in? false
                 :login-loading? false
                 :login-failed? true)})))

(rf/reg-event-db
 :hide-login-loading
 (fn [db]
   (assoc db :login-loading? false)))

(rf/reg-event-fx
 :logout
 (constantly
  {:local-store {:session-token nil}
   :cookies {:admin-token [nil {:max-age 60 :path "/"}]}
   :dispatch [:nav-login]}))

(rf/reg-event-db
 :clear-login-form
 (fn [db]
   (assoc db :login-failed? false)))

(defn login-page []
  (let [email (r/atom "")
        pwd (r/atom "")
        login-failed? (rf/subscribe [:login-failed?])
        login-loading? (rf/subscribe [:login-loading?])]
    (r/create-class
     {:component-will-unmount #(rf/dispatch [:clear-login-form])
      :reagent-render
      (fn []
        (if @login-loading?
          [cc/c-loader {:props {:style {:margin-top 175}}}]
          [:div.centerpiece
           [:img.logo {:src "https://s3.amazonaws.com/vetd-logos/vetd.svg"}]
           [:> ui/Form {:error @login-failed?}
            (when @login-failed?
              [:> ui/Message {:error true
                              :header "Incorrect email / password"
                              :content "Contact us at help@vetd.com"}])
            [:> ui/FormField
             [ui/input {:class "borderless"
                        :value @email
                        :placeholder "Email Address"
                        :autoFocus true
                        :spellCheck false
                        :on-change #(reset! email (-> % .-target .-value))}]]
            [:> ui/FormField
             [:> ui/Input {:class "borderless"
                           :type "password"
                           :placeholder "Password"
                           :onChange (fn [_ this]
                                       (reset! pwd (.-value this)))}]]
            [:> ui/Button {:fluid true
                           :on-click #(rf/dispatch [:login [@email @pwd]])}
             "Log In"]
            [:> ui/Divider {:horizontal true
                            :style {:margin "20px 0"}}
             "Sign Up"]
            [:> ui/ButtonGroup {:fluid true}
             [:> ui/Button {:color "teal"
                            :on-click #(rf/dispatch [:b/nav-signup])}
              "As a Buyer"]
             [:> ui/ButtonOr]
             [:> ui/Button {:color "blue"
                            :on-click #(rf/dispatch [:v/nav-signup])}
              "As a Vendor"]]]]))})))
