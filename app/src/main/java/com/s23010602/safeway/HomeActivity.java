package com.s23010602.safeway;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import android.widget.ImageButton;



import android.os.Bundle;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialButton mapButton = findViewById(R.id.btnMap);
        ImageButton searchButton = findViewById(R.id.btnSearch); // <- get the ImageButton
        Button manageButton = findViewById(R.id.btnManage);
        MaterialButton historyButton = findViewById(R.id.btnHistory);
        MaterialButton settingsButton = findViewById(R.id.btnSettings);

        mapButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MapActivity.class);
            startActivity(intent);
        });

        searchButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        manageButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ManageActivationActivity.class);
            startActivity(intent);
        });

        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AlertHistoryActivity.class);
            startActivity(intent);
        });

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

}}