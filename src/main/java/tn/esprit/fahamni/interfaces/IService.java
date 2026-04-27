package tn.esprit.fahamni.interfaces;

import java.util.List;

public interface IService<T> {
    void add(T t);
    void update(T t);
    void delete(T t);
    List<T> findAll();
    T findById(int id);
}
