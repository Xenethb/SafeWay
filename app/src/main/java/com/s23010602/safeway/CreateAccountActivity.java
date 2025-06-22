package com.s23010602.safeway;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class CreateAccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        Button nextButton = findViewById(R.id.nextButton);

        nextButton.setOnClickListener(view -> {
            Intent intent = new Intent(CreateAccountActivity.this, HomeActivity.class);
            startActivity(intent);
        });
    }
}