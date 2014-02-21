package org.wikipedia.createaccount;

import android.app.*;
import android.os.Bundle;
import android.support.v7.app.*;
import android.view.*;
import android.widget.*;
import com.mobsandgeeks.saripaar.*;
import com.mobsandgeeks.saripaar.annotation.*;
import org.mediawiki.api.json.*;
import org.wikipedia.*;
import org.wikipedia.editing.*;

public class CreateAccountActivity extends ActionBarActivity {
    @Required(order=1)
    private EditText usernameEdit;
    @Required(order=2)
    @Password(order=3)
    private EditText passwordEdit;
    @ConfirmPassword(order=4, messageResId=R.string.create_account_passwords_mismatch_error)
    private EditText passwordRepeatEdit;
    @Email(order=5, messageResId=R.string.create_account_email_error)
    private EditText emailEdit;

    private View primaryContainer;

    private WikipediaApp app;

    private ProgressDialog progressDialog;

    private CaptchaHandler captchaHandler;

    private NonEmptyValidator nonEmptyValidator;

    private CreateAccountResult createAccountResult;

    private Validator validator;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        app = (WikipediaApp) getApplicationContext();

        usernameEdit = (EditText) findViewById(R.id.create_account_username);
        passwordEdit = (EditText) findViewById(R.id.create_account_password);
        passwordRepeatEdit = (EditText) findViewById(R.id.create_account_password_repeat);
        emailEdit = (EditText) findViewById(R.id.create_account_email);
        primaryContainer = findViewById(R.id.create_account_primary_container);

        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getString(R.string.dialog_create_account_checking_progress));

        captchaHandler = new CaptchaHandler(this, app.getPrimarySite(), progressDialog, primaryContainer, R.string.create_account_activity_title);

        // We enable the menu item as soon as the username and password fields are filled
        // Tapping does further validation
        validator = new Validator(this);
        validator.setValidationListener(new Validator.ValidationListener() {
            @Override
            public void onValidationSucceeded() {
                doCreateAccount();
            }

            @Override
            public void onValidationFailed(View view, Rule<?> rule) {
                if (view instanceof EditText) {
                    ((EditText) view).setError(rule.getFailureMessage());
                } else {
                    throw new RuntimeException("This should not be happening");
                }
            }
        });

        nonEmptyValidator = new NonEmptyValidator(new NonEmptyValidator.ValidationChangedCallback() {
            @Override
            public void onValidationChanged(boolean isValid) {
                supportInvalidateOptionsMenu();
            }
        }, usernameEdit, passwordEdit, passwordRepeatEdit);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_create_account, menu);
        menu.findItem(R.id.menu_create_account).setEnabled(nonEmptyValidator.isValid());
        return true;
    }

    public void doCreateAccount() {
        String token = null, email = null;
        if (createAccountResult != null && createAccountResult instanceof CreateAccountTokenResult) {
            token = ((CreateAccountTokenResult) createAccountResult).getToken();
        }
        if (emailEdit.getText().length() != 0) {
            email = emailEdit.getText().toString();
        }
        new CreateAccountTask(this, usernameEdit.getText().toString(), passwordEdit.getText().toString(), email, token) {
            @Override
            public void onBeforeExecute() {
                progressDialog.show();
            }

            @Override
            public RequestBuilder buildRequest(Api api) {
                if (createAccountResult != null && createAccountResult instanceof CreateAccountTokenResult) {
                   return captchaHandler.populateBuilder(super.buildRequest(api));
                }
                return super.buildRequest(api);
            }

            @Override
            public void onFinish(final CreateAccountResult result) {
                createAccountResult = result;
                if (result instanceof CreateAccountTokenResult) {
                    captchaHandler.handleCaptcha(((CreateAccountTokenResult)result).getCaptchaResult());
                } else {
                    // Returns lowercase 'success', unlike every other API. GRR man, GRR
                    // Replace wen https://bugzilla.wikimedia.org/show_bug.cgi?id=61663 is fixed?
                    if (result.getResult().toLowerCase().equals("success")) {
                        finish();
                    } else if (result.getResult().equals("captcha-createaccount-fail")) {
                        // So for now we just need to do the entire set of requests again. sigh
                        // Eventually this should be fixed to have the new captcha info come back.
                        createAccountResult = null;
                        doCreateAccount();
                    } else {
                        throw new RuntimeException("Errored with " + result.getResult());
                    }
                }
            }
        }.execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_create_account:
                validator.validate();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}