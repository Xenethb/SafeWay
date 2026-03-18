package com.s23010602.safeway;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText etSearch;
    private RecyclerView rvUsers;
    private TextView tvNoResults; // Added for a "Real App" feel
    private UserAdapter userAdapter;

    private List<User> allUsers = new ArrayList<>();
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = findViewById(R.id.etSearch);
        rvUsers = findViewById(R.id.rvUsers);
        tvNoResults = findViewById(R.id.tvNoResults); // Make sure to add this to your XML!

        rvUsers.setLayoutManager(new LinearLayoutManager(this));

        userAdapter = new UserAdapter(new ArrayList<>(), user -> {
            startActivity(OtherUserActivity.makeIntent(SearchActivity.this, user.uid));
        });
        rvUsers.setAdapter(userAdapter);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        loadAllUsers();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                if (query.isEmpty()) {
                    userAdapter.updateList(new ArrayList<>());
                    if (tvNoResults != null) tvNoResults.setVisibility(View.GONE);
                } else {
                    filterUsers(query);
                }
            }
        });
    }

    private void loadAllUsers() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Single value event is good for search to avoid the list jumping around while typing
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    User user = snap.getValue(User.class);
                    if (user != null) {
                        user.uid = snap.getKey();

                        // Exclude self immediately to keep the 'allUsers' list clean
                        if (user.uid != null && !user.uid.equals(myUid)) {
                            allUsers.add(user);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterUsers(String query) {
        List<User> filtered = new ArrayList<>();

        for (User user : allUsers) {
            String email = user.email != null ? user.email.toLowerCase() : "";
            String username = user.username != null ? user.username.toLowerCase() : "";

            if (email.contains(query) || username.contains(query)) {
                filtered.add(user);
            }
        }

        userAdapter.updateList(filtered);

        // UI Feedback: Show "No Results" if the filtered list is empty
        if (tvNoResults != null) {
            tvNoResults.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
}