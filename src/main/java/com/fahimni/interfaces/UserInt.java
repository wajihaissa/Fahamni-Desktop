package com.fahimni.interfaces;

import java.util.List;

public interface UserInt<T> {
    void add(T t);
    void delete(T t);
    void update(T t);
    List<T> getAll();
}