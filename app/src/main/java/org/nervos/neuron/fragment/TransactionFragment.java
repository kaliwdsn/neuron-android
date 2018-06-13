package org.nervos.neuron.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;
import org.nervos.neuron.R;
import org.nervos.neuron.activity.TransactionDetailActivity;
import org.nervos.neuron.item.TransactionItem;
import org.nervos.neuron.item.WalletItem;
import org.nervos.neuron.util.Blockies;
import org.nervos.neuron.util.db.DBWalletUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static org.nervos.neuron.activity.TransactionDetailActivity.EXTRA_TRANSACTION;

public class TransactionFragment extends BaseFragment {

    public static final String TAG = TransactionFragment.class.getName();

    private static final String TRANSACTION_URL = "http://47.97.171.140:4000/api/transactions";

    private List<TransactionItem> transactionItemList = new ArrayList<>();
    private WalletItem walletItem;

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TransactionAdapter adapter = new TransactionAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction, container, false);
        recyclerView = view.findViewById(R.id.transaction_recycler);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        walletItem = DBWalletUtil.getCurrentWallet(getContext());
        initAdapter();
        getTransactionList();
        initRefreshData();
    }

    private void initAdapter() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Intent intent = new Intent(getActivity(), TransactionDetailActivity.class);
                intent.putExtra(EXTRA_TRANSACTION, transactionItemList.get(position));
                startActivity(intent);
            }
        });
    }


    private void initRefreshData() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(false);

                    }
                },500);
            }
        });
    }


    private void getTransactionList() {
        showProgressBar();
        OkHttpClient mOkHttpClient = new OkHttpClient();
        final Request request = new Request.Builder().url(TRANSACTION_URL).build();
        Call call = mOkHttpClient.newCall(request);
        Observable.fromCallable(new Callable<String>() {
            @Override
            public String call() {
                try {
                    return call.execute().body().string();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }).subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<String>() {
            @Override
            public void onCompleted() {
                dismissProgressBar();
            }
            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                dismissProgressBar();
            }
            @Override
            public void onNext(String res) {
                if (TextUtils.isEmpty(res)) {
                    Toast.makeText(getContext(), "网络请求错误", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    JSONObject result = new JSONObject(res);
                    Type type = new TypeToken<ArrayList<TransactionItem>>() {}.getType();
                    transactionItemList = new Gson().fromJson(result.getString("result"), type);
                    adapter.notifyDataSetChanged();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        public static final int VIEW_TYPE_ITEM = 1;
        public static final int VIEW_TYPE_EMPTY = 0;

        public OnItemClickListener onItemClickListener;

        public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
            this.onItemClickListener = onItemClickListener;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
            if (viewType == VIEW_TYPE_EMPTY) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_empty_view, parent, false);
                ((TextView)view.findViewById(R.id.empty_text)).setText(R.string.empty_no_transaction_data);
                return new RecyclerView.ViewHolder(view){};
            }
            TransactionViewHolder holder = new TransactionViewHolder(LayoutInflater.from(
                    getActivity()).inflate(R.layout.item_transaction_list, parent,
                    false));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof TransactionViewHolder) {
                TransactionViewHolder viewHolder = (TransactionViewHolder)holder;
                if (walletItem != null) {
                    viewHolder.walletImage.setImageBitmap(Blockies.createIcon(walletItem.address));
                }
                viewHolder.transactionIdText.setText(transactionItemList.get(position).id);
                viewHolder.transactionAmountText.setText(transactionItemList.get(position).value);
                viewHolder.transactionChainNameText.setText(transactionItemList.get(position).chainName);
                viewHolder.transactionTimeText.setText(transactionItemList.get(position).getDate());
                viewHolder.itemView.setTag(position);
            }
        }

        @Override
        public int getItemCount() {
            if (transactionItemList.size() == 0) {
                return 1;
            }
            return transactionItemList.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (transactionItemList.size() == 0) {
                return VIEW_TYPE_EMPTY;
            }
            return VIEW_TYPE_ITEM;
        }

        class  TransactionViewHolder extends RecyclerView.ViewHolder {
            CircleImageView walletImage;
            TextView transactionIdText;
            TextView transactionAmountText;
            TextView transactionTimeText;
            TextView transactionChainNameText;

            public TransactionViewHolder (View view) {
                super(view);
                view.setOnClickListener(v -> {
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(v, (int)v.getTag());
                    }
                });
                walletImage = view.findViewById(R.id.wallet_photo);
                transactionIdText = view.findViewById(R.id.transaction_id_text);
                transactionTimeText = view.findViewById(R.id.transaction_time_text);
                transactionAmountText = view.findViewById(R.id.transaction_amount);
                transactionChainNameText = view.findViewById(R.id.transaction_chain_name);
            }
        }
    }

    private interface OnItemClickListener{
        void onItemClick(View view, int position);
    }

}
