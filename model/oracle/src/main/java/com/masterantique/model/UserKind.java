package com.masterantique.model;

/**
 * The kinds of user in the legacy TPH table {@code users}: each name is exactly the {@code discriminator} value, and
 * also the name of the matching row in {@code roles}. Use {@link #name()} when a query needs the string.
 */
public enum UserKind {
    Customer,
    Employee,
    Manager
}
