package pershkin.perspvefishing.model;

public enum RodType {
    ROD1("rod1"),
    ROD2("rod2"),
    ROD3("rod3");

    private final String id;

    RodType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static RodType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (RodType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
