package com.s23010602.safeway;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FriendsActivity extends AppCompatActivity {

    private RecyclerView rvFriends;
    private EditText etSearch;
    private TextView tvEmptyState; // For "No Friends" message
    private UserAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        rvFriends = findViewById(R.id.rvFriends);
        etSearch = findViewById(R.id.etSearch);
        tvEmptyState = findViewById(R.id.tvEmptyState); // Make sure to add to XML

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        adapter = new UserAdapter(userList, user -> {
            startActivity(OtherUserActivity.makeIntent(this, user.uid));
        });

        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);

        fetchFriends();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterFriends(s.toString());
            }
        });
    }

    private void fetchFriends() {
        DatabaseReference friendsRef = FirebaseDatabase.getInstance().getReference("friends").child(myUid);

        // Real-time listener: If someone un-friends you, they disappear instantly!
        friendsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userList.clear();
                if (!snapshot.exists()) {
                    updateUI(true);
                    return;
                }

                // Count friends to know when all data is fetched
                long totalFriends = snapshot.getChildrenCount();
                final int[] loadedCount = {0};

                for (DataSnapshot friendSnap : snapshot.getChildren()) {
                    String friendUid = friendSnap.getKey();

                    // Fetch ONLY this specific friend's data
                    FirebaseDatabase.getInstance().getReference("users").child(friendUid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnap) {
                                    User user = userSnap.getValue(User.class);
                                    if (user != null) {
                                        user.uid = userSnap.getKey();
                                        userList.add(user);
                                    }

                                    loadedCount[0]++;
                                    if (loadedCount[0] == totalFriends) {
                                        updateUI(false);
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError e) {}
                            });
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FriendsActivity.this, "Error loading list", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterFriends(String query) {
        List<User> filtered = new ArrayList<>();
        String q = query.toLowerCase().trim();

        for (User u : userList) {
            String name = (u.username != null) ? u.username.toLowerCase() : "";
            String email = (u.email != null) ? u.email.toLowerCase() : "";

            if (name.contains(q) || email.contains(q)) {
                filtered.add(u);
            }
        }
        adapter.updateList(filtered);
    }

    private void updateUI(boolean isEmpty) {
        if (isEmpty) {
            userList.clear();
            adapter.updateList(new ArrayList<>());
            tvEmptyState.setVisibility(View.VISIBLE);
            rvFriends.setVisibility(View.GONE);
        } else {
            adapter.updateList(userList);
            tvEmptyState.setVisibility(View.GONE);
            rvFriends.setVisibility(View.VISIBLE);
        }
    }
}