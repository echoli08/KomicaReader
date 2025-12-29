package com.komica.reader.model;

import java.util.List;

public class BoardCategory {
    private String name;
    private List<Board> boards;

    public BoardCategory(String name, List<Board> boards) {
        this.name = name;
        this.boards = boards;
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
}
