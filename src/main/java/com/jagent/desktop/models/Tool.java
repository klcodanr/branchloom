package com.jagent.desktop.models;

import java.io.Serializable;

public record Tool(String label, String command) implements Serializable {
    private static final long serialVersionUID = 1L;
}
