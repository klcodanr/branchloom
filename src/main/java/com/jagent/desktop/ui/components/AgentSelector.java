package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Agent;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JComboBox;

/** Agent selection control with the application's standard sizing and naming. */
public final class AgentSelector extends JComboBox<Agent> {
    public AgentSelector(final String name, final List<Agent> agents) {
        super(agents.toArray(new Agent[0]));
        setName(name);
        setPreferredSize(new Dimension(350, getPreferredSize().height));
    }
}
