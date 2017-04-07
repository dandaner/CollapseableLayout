package com.demon.collapseablelayout;

import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.View;

import com.demon.library.CollapseableLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CollapseableLayout.IOnFlingListener, CollapseableLayout.IOnOffsetChangedListener {

    private View headerContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initRecyclerView();

        CollapseableLayout layout = (CollapseableLayout) findViewById(R.id.collapseablelayout);
        layout.addOnOffsetChangedListener(this);
        layout.addOnFlingListener(this);

        headerContent = findViewById(R.id.header_content);
    }

    private void initRecyclerView() {
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        MyAdapter adapter = new MyAdapter(this);
        adapter.replaceAll(prepareDatas());
        recyclerView.setAdapter(adapter);
    }

    private List<Pair<String, String>> prepareDatas() {
        ArrayList<Pair<String, String>> result = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            result.add(new Pair<>("Alex_" + i, "say hello!  " + i));
        }
        return result;
    }

    @Override
    public void onFling(float velocityY) {

    }

    @Override
    public void onOffsetChanged(int verticalOffset, int maxOffset) {
        float alpha = ((float) maxOffset / 3 - verticalOffset) * 3 / maxOffset;
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 1) {
            alpha = 1;
        }
        headerContent.setAlpha(alpha);
        ViewCompat.setTranslationY(headerContent, verticalOffset * 0.5f);
    }
}
