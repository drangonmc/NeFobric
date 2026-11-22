package net.minecraftforge.common.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;

/** 把几个列表首尾相接看成一个只读列表，不复制元素。 */
public class ConcatenatedListView<T> implements List<T> {
    private final List<List<? extends T>> lists;

    private ConcatenatedListView(List<List<? extends T>> lists) {
        this.lists = lists;
    }

    public static <T> List<T> of(List<? extends T> list) {
        @SuppressWarnings("unchecked")
        List<T> l = (List<T>) list;
        return l;
    }

    @SafeVarargs
    public static <T> ConcatenatedListView<T> of(List<? extends T>... lists) {
        return new ConcatenatedListView<>(List.of(lists));
    }

    @Override
    public int size() {
        int n = 0;
        for (List<? extends T> l : lists) {
            n += l.size();
        }
        return n;
    }

    @Override
    public boolean isEmpty() {
        for (List<? extends T> l : lists) {
            if (!l.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(Object o) {
        for (List<? extends T> l : lists) {
            if (l.contains(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public T get(int index) {
        if (index >= 0) {
            int i = index;
            for (List<? extends T> l : lists) {
                if (i < l.size()) {
                    return l.get(i);
                }
                i -= l.size();
            }
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public int indexOf(Object o) {
        int offset = 0;
        for (List<? extends T> l : lists) {
            int i = l.indexOf(o);
            if (i >= 0) {
                return offset + i;
            }
            offset += l.size();
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int offset = size();
        for (int k = lists.size() - 1; k >= 0; k--) {
            List<? extends T> l = lists.get(k);
            offset -= l.size();
            int i = l.lastIndexOf(o);
            if (i >= 0) {
                return offset + i;
            }
        }
        return -1;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int list;
            private Iterator<? extends T> current = lists.isEmpty() ? null : lists.get(0).iterator();

            @Override
            public boolean hasNext() {
                while (current != null && !current.hasNext()) {
                    list++;
                    current = list < lists.size() ? lists.get(list).iterator() : null;
                }
                return current != null;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), size(), Spliterator.ORDERED);
    }

    @Override
    public ListIterator<T> listIterator() {
        return copy().listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return copy().listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return copy().subList(fromIndex, toIndex);
    }

    @Override
    public Object[] toArray() {
        return copy().toArray();
    }

    @Override
    public <U> U[] toArray(U[] a) {
        return copy().toArray(a);
    }

    private List<T> copy() {
        List<T> out = new ArrayList<>(size());
        lists.forEach(out::addAll);
        return java.util.Collections.unmodifiableList(out);
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof List<?> other && copy().equals(other);
    }

    @Override
    public int hashCode() {
        return copy().hashCode();
    }

    @Override
    public String toString() {
        return copy().toString();
    }
}
