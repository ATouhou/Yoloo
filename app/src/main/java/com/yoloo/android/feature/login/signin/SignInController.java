package com.yoloo.android.feature.login.signin;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.bluelinelabs.conductor.RouterTransaction;
import com.yoloo.android.R;
import com.yoloo.android.data.repository.user.UserRepository;
import com.yoloo.android.data.repository.user.datasource.UserDiskDataStore;
import com.yoloo.android.data.repository.user.datasource.UserRemoteDataStore;
import com.yoloo.android.feature.feed.home.FeedHomeController;
import com.yoloo.android.feature.login.AuthUI;
import com.yoloo.android.feature.login.FacebookProvider;
import com.yoloo.android.feature.login.GoogleProvider;
import com.yoloo.android.feature.login.IdpProvider;
import com.yoloo.android.feature.login.IdpResponse;
import com.yoloo.android.framework.MvpController;
import com.yoloo.android.util.FormUtil;
import com.yoloo.android.util.KeyboardUtil;

import java.net.SocketTimeoutException;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import butterknife.OnFocusChange;
import timber.log.Timber;

public class SignInController extends MvpController<SignInView, SignInPresenter>
    implements SignInView, IdpProvider.IdpCallback {

  @BindView(R.id.et_login_email) AutoCompleteTextView etEmail;
  @BindView(R.id.et_login_password) EditText etPassword;
  @BindView(R.id.toolbar_sign_in) Toolbar toolbar;

  @BindString(R.string.error_google_play_services) String errorGooglePlayServicesString;
  @BindString(R.string.error_auth_failed) String errorAuthFailedString;
  @BindString(R.string.error_server_down) String errorServerDownString;
  @BindString(R.string.label_loading) String loadingString;
  @BindString(R.string.error_field_required) String errorFieldRequiredString;
  @BindString(R.string.error_invalid_email) String errorInvalidEmail;
  @BindString(R.string.error_invalid_password) String errorInvalidPassword;

  private ProgressDialog progressDialog;

  private IdpProvider idpProvider;

  public static SignInController create() {
    return new SignInController();
  }

  @Override
  protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    return inflater.inflate(R.layout.controller_sign_in, container, false);
  }

  @Override protected void onViewBound(@NonNull View view) {
    setHasOptionsMenu(true);
    setupToolbar();

    getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
  }

  @Override protected void onAttach(@NonNull View view) {
    super.onAttach(view);
    if (idpProvider instanceof GoogleProvider) {
      ((GoogleProvider) idpProvider).connect();
    }
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    if (idpProvider instanceof GoogleProvider) {
      ((GoogleProvider) idpProvider).disconnect();
    }

    if (idpProvider != null) {
      idpProvider.setAuthenticationCallback(null);
    }
  }

  @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      getRouter().handleBack();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @NonNull @Override public SignInPresenter createPresenter() {
    return new SignInPresenter(
        UserRepository.getInstance(
            UserRemoteDataStore.getInstance(),
            UserDiskDataStore.getInstance()));
  }

  @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    idpProvider.onActivityResult(requestCode, resultCode, data);
  }

  @OnClick(R.id.btn_google_sign_in) void signInWithGoogle() {
    idpProvider = getIdpProvider(AuthUI.GOOGLE_PROVIDER);
    idpProvider.setAuthenticationCallback(this);

    idpProvider.startLogin(this);
  }

  @OnClick(R.id.btn_facebook_sign_in) void signInWithFacebook() {
    idpProvider = getIdpProvider(AuthUI.FACEBOOK_PROVIDER);
    idpProvider.setAuthenticationCallback(this);

    idpProvider.startLogin(this);
  }

  @OnClick(R.id.btn_login_ready) void signInWithEmail() {
    etEmail.setError(null);
    etPassword.setError(null);

    final String email = etEmail.getText().toString();
    final String password = etPassword.getText().toString();

    boolean cancel = false;
    View focusView = null;

    // Check for a valid email address.
    if (TextUtils.isEmpty(email)) {
      etEmail.setError(errorFieldRequiredString);
      focusView = etEmail;
      cancel = true;
    } else if (!FormUtil.isEmailAddress(email)) {
      etEmail.setError(errorInvalidEmail);
      focusView = etEmail;
      cancel = true;
    }

    // Check for a valid password, if the user entered one.
    if (TextUtils.isEmpty(password)) {
      etPassword.setError(errorFieldRequiredString);
      focusView = etPassword;
      cancel = true;
    } else if (!FormUtil.isPasswordValid(password)) {
      etPassword.setError(errorInvalidPassword);
      focusView = etPassword;
      cancel = true;
    }

    if (cancel) {
      // There was an error; don't attempt login and focus the first
      // form field with an error.
      focusView.requestFocus();
    } else {
      KeyboardUtil.hideKeyboard(etPassword);

      getPresenter().signIn(email, password);
    }
  }

  @OnFocusChange(R.id.et_login_email) void onEmailFocusChanged(boolean hasFocus) {
    if (hasFocus) {
      FormUtil.populateEmail(getActivity(), etEmail);
    }
  }

  @OnEditorAction(R.id.et_login_password) boolean onEditorAction(int actionId) {
    if (actionId == R.id.sign_up || actionId == EditorInfo.IME_NULL) {
      signInWithEmail();
      return true;
    }
    return false;
  }

  @Override public void onSignedIn() {
    getRouter().setRoot(RouterTransaction.with(FeedHomeController.create()));
    //getParentController().getRouter().setRoot(RouterTransaction.with(FeedHomeController.create
    // ()));
  }

  @Override public void onError(Throwable t) {
    Timber.e(t);
    Snackbar.make(getView(), errorAuthFailedString, Snackbar.LENGTH_SHORT).show();

    if (t instanceof SocketTimeoutException) {
      Snackbar.make(getView(), errorServerDownString, Snackbar.LENGTH_SHORT).show();
    }

    // Backend didn't processed, sign out account.
    AuthUI.getInstance().signOut(getActivity());
  }

  @Override public void onShowLoading() {
    if (progressDialog == null) {
      progressDialog = new ProgressDialog(getActivity());
      progressDialog.setMessage(loadingString);
      progressDialog.setIndeterminate(true);
    }

    progressDialog.show();
  }

  @Override public void onHideLoading() {
    if (progressDialog != null && progressDialog.isShowing()) {
      progressDialog.dismiss();
    }
  }

  @Override public void onSuccess(IdpResponse idpResponse) {
    getPresenter().signIn(idpResponse);
  }

  @Override public void onFailure(Bundle extra) {
    //Snackbar.make(getView(), extra.getString("error", null), Snackbar.LENGTH_SHORT).show();
  }

  private IdpProvider getIdpProvider(String providerId) {
    final AuthUI.IdpConfig config = new AuthUI.IdpConfig.Builder(providerId).build();

    switch (providerId) {
      case AuthUI.GOOGLE_PROVIDER:
        return new GoogleProvider(this, config);
      case AuthUI.FACEBOOK_PROVIDER:
        return new FacebookProvider(config);
      default:
        throw new UnsupportedOperationException("Given providerId is not valid!");
    }
  }

  private void setupToolbar() {
    setSupportActionBar(toolbar);

    // addPost back arrow to toolbar
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setDisplayShowTitleEnabled(false);
      ab.setDisplayHomeAsUpEnabled(true);
      ab.setDisplayShowHomeEnabled(true);
    }
  }
}
