package tn.esprit.fahamni.test;

import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.services.CategoryService;

public class TestBackend {
    public static void main(String[] args) {
        System.out.println("--- STARTING BACKEND TEST ---");

        // 1. Initialize the Service
        CategoryService cs = new CategoryService();

        // 2. Create a new Category object
        Category cat = new Category();
        cat.setName("Informatique");
        cat.setSlug("informatique-slug");

        // 3. Push it to the database
        System.out.println("Adding category to database...");
        cs.ajouter(cat);
        System.out.println("Success! Category added.");

        // 4. Retrieve and display to prove it worked
        System.out.println("List of Categories in DB:");
        for (Category c : cs.afficherList()) {
            System.out.println("ID: " + c.getId() + " | Name: " + c.getName() + " | Slug: " + c.getSlug());
        }
    }
}
