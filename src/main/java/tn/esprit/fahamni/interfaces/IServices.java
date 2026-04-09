package tn.esprit.fahamni.interfaces;

import java.util.List;

public interface IServices<T> {

    void add(T t);

    List<T> getAll();

    void update(T t);

    void delete(T t);
}
