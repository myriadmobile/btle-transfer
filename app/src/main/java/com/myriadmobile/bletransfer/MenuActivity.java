package com.myriadmobile.bletransfer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class MenuActivity extends Activity implements View.OnClickListener{

    @InjectView(R.id.btCentral)
    Button btCentral;
    @InjectView(R.id.btPeripheral)
    Button btPeripheral;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        ButterKnife.inject(this);

        btCentral.setOnClickListener(this);
        btPeripheral.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btCentral:
                startActivity(new Intent(this, CentralActivity.class));
                break;
            case R.id.btPeripheral:
                startActivity(new Intent(this, PeripheralActivity.class));
                break;
        }
    }
}
