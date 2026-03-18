package com.s23010602.safeway;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    private List<String> originalList; // Full list of friend names
    private List<String> filteredList; // Filtered list for search/filtering

    // Constructor: receives the initial list of friends
    public FriendAdapter(List<String> friendList) {
        this.originalList = friendList;
        this.filteredList = new ArrayList<>(friendList); // Start with all friends visible
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for a single friend item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.friend_item, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        // Get friend name at current position in filtered list
        String friendName = filteredList.get(position);
        holder.name.setText(friendName); // Display friend name in TextView
    }

    @Override
    public int getItemCount() {
        return filteredList.size(); // Number of items currently visible
    }

    // Filter the friend list based on a search string
    public void filter(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(originalList); // No filter: show all
        } else {
            for (String name : originalList) {
                if (name.toLowerCase().contains(text.toLowerCase())) { // Case-insensitive match
                    filteredList.add(name);
                }
            }
        }
        notifyDataSetChanged(); // Refresh RecyclerView
    }

    // ViewHolder for a single friend item
    static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView name;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvFriendName); // TextView for friend name
        }
    }
}
