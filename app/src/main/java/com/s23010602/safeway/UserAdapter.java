package com.s23010602.safeway;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<User> userList;
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserAdapter(List<User> userList, OnUserClickListener listener) {
        this.userList = userList != null ? userList : new ArrayList<>();
        this.listener = listener;
    }

    public void updateList(List<User> updatedList) {
        this.userList = updatedList != null ? updatedList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure 'friend_item.xml' exists in your layout folder
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.friend_item, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);

        holder.friendName.setText(user.username);

        // UX Improvement: Show email as a subtitle if the view exists
        if (holder.friendEmail != null) {
            holder.friendEmail.setText(user.email);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView friendName;
        TextView friendEmail; // Added for better identification

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            friendName = itemView.findViewById(R.id.tvFriendName);
            friendEmail = itemView.findViewById(R.id.tvFriendEmail); // Optional subtitle
        }
    }
}