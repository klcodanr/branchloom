package com.jagent.desktop.services;

public enum GitIntegrationStrategy {
    MERGE {
        @Override
        /* default */ String command(final String ref) {
            return "git merge --no-edit " + PlatformCommands.shellQuote(ref);
        }

        @Override
        /* default */ String verb() {
            return "merge";
        }
    },
    REBASE {
        @Override
        /* default */ String command(final String ref) {
            return "git rebase " + PlatformCommands.shellQuote(ref);
        }

        @Override
        /* default */ String verb() {
            return "rebase";
        }
    };

    /* default */ abstract String command(String ref);

    /* default */ abstract String verb();
}
