package adris.altoclef.commands;

class CustomTaskConfig {
    public String prefix = "custom";
    public CustomTaskEntry[] customTasks = new CustomTaskEntry[0];

    static class CustomTaskEntry {
        public String name;
        public String description;
        public CustomSubTaskEntry[] tasks = new CustomSubTaskEntry[0];

        static class CustomSubTaskEntry {
            public String command;
            public String[][] parameters = new String[0][];
        }
    }
}
