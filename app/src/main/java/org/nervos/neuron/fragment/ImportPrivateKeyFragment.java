package org.nervos.neuron.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.uuzuche.lib_zxing.activity.CodeUtils;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;

import org.nervos.neuron.R;
import org.nervos.neuron.activity.MainActivity;
import org.nervos.neuron.activity.QrCodeActivity;
import org.nervos.neuron.fragment.WalletsFragment.view.WalletsFragment;
import org.nervos.neuron.item.WalletItem;
import org.nervos.neuron.util.NumberUtil;
import org.nervos.neuron.util.db.SharePrefUtil;
import org.nervos.neuron.util.permission.PermissionUtil;
import org.nervos.neuron.util.permission.RuntimeRationale;
import org.nervos.neuron.util.db.DBWalletUtil;
import org.nervos.neuron.crypto.WalletEntity;
import org.web3j.crypto.CipherException;
import org.web3j.utils.Numeric;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ImportPrivateKeyFragment extends BaseFragment {

    private static final int REQUEST_CODE = 0x01;
    private AppCompatEditText privateKeyEdit;
    private AppCompatEditText walletNameEdit;
    private AppCompatEditText passwordEdit;
    private AppCompatEditText rePasswordEdit;
    private AppCompatButton importButton;
    private ImageView scanImage;

    ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_import_private_key, container, false);
        privateKeyEdit = view.findViewById(R.id.edit_wallet_private_key);
        walletNameEdit = view.findViewById(R.id.edit_wallet_name);
        passwordEdit = view.findViewById(R.id.edit_wallet_password);
        rePasswordEdit = view.findViewById(R.id.edit_wallet_repassword);
        importButton = view.findViewById(R.id.import_private_key_button);
        scanImage = view.findViewById(R.id.wallet_scan);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initListener();
        checkWalletStatus();
    }


    private void initListener() {
        importButton.setOnClickListener(view -> {
            if (!NumberUtil.isPasswordOk(passwordEdit.getText().toString().trim())) {
                Toast.makeText(getContext(), R.string.password_weak, Toast.LENGTH_SHORT).show();
            } else if (!TextUtils.equals(passwordEdit.getText().toString().trim(),
                    rePasswordEdit.getText().toString().trim())) {
                Toast.makeText(getContext(), R.string.password_not_same, Toast.LENGTH_SHORT).show();
            } else if (DBWalletUtil.checkWalletName(getContext(), walletNameEdit.getText().toString().trim())) {
                Toast.makeText(getContext(), R.string.wallet_name_exist, Toast.LENGTH_SHORT).show();
            } else {
                cachedThreadPool.execute(() -> generateAndSaveWallet());
            }
        });
        scanImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AndPermission.with(getActivity())
                        .runtime().permission(Permission.Group.CAMERA)
                        .rationale(new RuntimeRationale())
                        .onGranted(permissions -> {
                            Intent intent = new Intent(getActivity(), QrCodeActivity.class);
                            startActivityForResult(intent, REQUEST_CODE);
                        })
                        .onDenied(permissions -> PermissionUtil.showSettingDialog(getActivity(), permissions))
                        .start();
            }
        });
    }

    private void generateAndSaveWallet() {
        passwordEdit.post(() -> showProgressBar(R.string.wallet_importing));
        WalletEntity walletEntity;
        try {
            walletEntity = WalletEntity.fromPrivateKey(
                    Numeric.toBigInt(privateKeyEdit.getText().toString().trim()));
        } catch (Exception e) {
            e.printStackTrace();
            passwordEdit.post(() -> {
                dismissProgressBar();
                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return;
        }
        if (walletEntity == null || DBWalletUtil.checkWalletAddress(getContext(), walletEntity.getAddress())) {
            passwordEdit.post(() -> {
                dismissProgressBar();
                Toast.makeText(getContext(), R.string.wallet_name_exist, Toast.LENGTH_SHORT).show();
            });
            return;
        }
        WalletItem walletItem = WalletItem.fromWalletEntity(
                passwordEdit.getText().toString().trim(), walletEntity);
        walletItem.name = walletNameEdit.getText().toString().trim();
        walletItem = DBWalletUtil.addOriginTokenToWallet(getContext(), walletItem);
        DBWalletUtil.saveWallet(getContext(), walletItem);
        SharePrefUtil.putCurrentWalletName(walletItem.name);
        passwordEdit.post(() -> {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(MainActivity.EXTRA_TAG, WalletsFragment.TAG);
            startActivity(intent);
            Toast.makeText(getContext(), R.string.wallet_export_success, Toast.LENGTH_SHORT).show();
            dismissProgressBar();
        });
    }


    private boolean isWalletValid() {
        return check1 && check2 && check3 && check4;
    }

    private void setCreateButtonStatus(boolean status) {
        importButton.setBackgroundResource(status ?
                R.drawable.button_corner_blue_shape : R.drawable.button_corner_gray_shape);
        importButton.setEnabled(status);
    }


    private boolean check1 = false, check2 = false, check3 = false, check4 = false;

    private void checkWalletStatus() {
        walletNameEdit.addTextChangedListener(new WalletTextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                super.onTextChanged(charSequence, i, i1, i2);
                check1 = !TextUtils.isEmpty(walletNameEdit.getText().toString().trim());
                setCreateButtonStatus(isWalletValid());
            }
        });
        passwordEdit.addTextChangedListener(new WalletTextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                super.onTextChanged(charSequence, i, i1, i2);
                check2 = !TextUtils.isEmpty(passwordEdit.getText().toString().trim())
                        && passwordEdit.getText().toString().trim().length() >= 8;
                setCreateButtonStatus(isWalletValid());
            }
        });

        rePasswordEdit.addTextChangedListener(new WalletTextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                super.onTextChanged(charSequence, i, i1, i2);
                check3 = !TextUtils.isEmpty(rePasswordEdit.getText().toString().trim())
                        && rePasswordEdit.getText().toString().trim().length() >= 8;
                setCreateButtonStatus(isWalletValid());
            }
        });
        privateKeyEdit.addTextChangedListener(new WalletTextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                super.onTextChanged(charSequence, i, i1, i2);
                check4 = !TextUtils.isEmpty(privateKeyEdit.getText().toString().trim());
                setCreateButtonStatus(isWalletValid());
            }
        });

    }


    private static class WalletTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (null != data) {
                Bundle bundle = data.getExtras();
                if (bundle == null) {
                    return;
                }
                if (bundle.getInt(CodeUtils.RESULT_TYPE) == CodeUtils.RESULT_SUCCESS) {
                    String result = bundle.getString(CodeUtils.RESULT_STRING);
                    privateKeyEdit.setText(result);
                } else if (bundle.getInt(CodeUtils.RESULT_TYPE) == CodeUtils.RESULT_FAILED) {
                    Toast.makeText(getActivity(), R.string.qrcode_handle_fail, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
