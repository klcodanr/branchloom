package com.jagent.desktop.ui.board;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jagent.desktop.models.board.BoardItem;
import java.util.Map;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BoardRuleEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoardRuleEvaluator.class);
    private final Gson gson = new Gson();
    private final JexlEngine engine = new JexlBuilder().strict(false).silent(false).create();

    public boolean matches(final BoardItem item, final String rule) {
        if (rule == null || rule.isBlank()) {
            return true;
        }
        try {
            final JexlExpression expression = engine.createExpression(rule);
            final Object result = expression.evaluate(context(item));
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to evaluate board rule: {}", rule, exception);
            return false;
        }
    }

    private JexlContext context(final BoardItem item) {
        final Map<String, Object> values =
                gson.fromJson(gson.toJson(item), new TypeToken<Map<String, Object>>() {}.getType());
        values.put("source", item.source().name());
        values.put("id", item.id());
        values.put("title", item.title());
        values.put("description", item.description());
        values.put("url", item.url());
        return new MapContext(values);
    }
}
