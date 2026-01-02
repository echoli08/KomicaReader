package com.komica.reader.model;

import java.util.List;

import java.io.Serializable;

public class BoardCategory implements Serializable {
    private String name;
    private List<Board> boards;
    private boolean expanded;

    public BoardCategory(String name, List<Board> boards) {
        this.name = name;
        this.boards = boards;
        this.expanded = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Board> getBoards() {
        return boards;
    }

    public void setBoards(List<Board> boards) {
        this.boards = boards;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
