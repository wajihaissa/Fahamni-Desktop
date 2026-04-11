package com.fahimni.test;

import com.fahimni.models.User;
import com.fahimni.services.UserServices;

import java.util.Arrays;
import java.util.List;

public class main {
    public static void main(String[] args) {
        UserServices service = new UserServices();r

        // Test Add
        System.out.println("=== Adding Users ===");
        User user1 = new User("ahmed@example.com", "password123", "Ahmed Ben Ali", true);
        User user2 = new User("sara@example.com", "password456", "Sara Mansouri", true);
        service.add(user1);
        service.add(user2);

        // Test GetAll
        System.out.println("\n=== All Users ===");
        List<User> users = service.getAll();
        System.out.println(users);

        // Test Update (if users exist)
        if (!users.isEmpty()) {
            System.out.println("\n=== Updating First User ===");
            User toUpdate = users.get(0);
            toUpdate.setFullName("Ahmed Ben Ali Updated");
            toUpdate.setStatus(false);
            service.update(toUpdate);
        }

        // Test Delete (if users exist)
        if (users.size() >= 2) {
            System.out.println("\n=== Deleting Second User ===");
            service.delete(users.get(1));
        }

        // Final list
        System.out.println("\n=== Final Users ===");
        users = service.getAll();
        System.out.println(users);
    }
}