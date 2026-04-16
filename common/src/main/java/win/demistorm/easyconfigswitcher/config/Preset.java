package win.demistorm.easyconfigswitcher.config;

import java.util.Objects;

public class Preset {
    private final String name;
    private int displayOrder;
    private String description;

    public Preset(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOrder() {
        return displayOrder;
    }

    public void setOrder(int order) {
        this.displayOrder = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Preset preset = (Preset) o;
        return Objects.equals(name, preset.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Preset{" +
                "name='" + name + '\'' +
                ", displayOrder=" + displayOrder +
                '}';
    }
}
