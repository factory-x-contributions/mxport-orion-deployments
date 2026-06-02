package org.eclipse.edc.common.spi.security.mosquitto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a Mosquitto Dynamic Security command structure.
 * Used to create users, roles, ACLs, and assign roles to users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MosquittoCommand {

    @JsonProperty("commands")
    private List<Command> commands;

    public MosquittoCommand(List<Command> commands) {
        this.commands = commands;
    }

    public List<Command> getCommands() {
        return commands;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Command {
        @JsonProperty("command")
        private String command;

        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;

        @JsonProperty("rolename")
        private String rolename;

        @JsonProperty("acltype")
        private String acltype;

        @JsonProperty("topic")
        private String topic;

        @JsonProperty("allow")
        private Boolean allow;

        public Command() {
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRolename() {
            return rolename;
        }

        public void setRolename(String rolename) {
            this.rolename = rolename;
        }

        public String getAcltype() {
            return acltype;
        }

        public void setAcltype(String acltype) {
            this.acltype = acltype;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public Boolean getAllow() {
            return allow;
        }

        public void setAllow(Boolean allow) {
            this.allow = allow;
        }

        public static Command createClient(String username, String password) {
            Command cmd = new Command();
            cmd.command = "createClient";
            cmd.username = username;
            cmd.password = password;
            return cmd;
        }

        public static Command createRole(String rolename) {
            Command cmd = new Command();
            cmd.command = "createRole";
            cmd.rolename = rolename;
            return cmd;
        }

        public static Command addRoleAcl(String rolename, String acltype, String topic, boolean allow) {
            Command cmd = new Command();
            cmd.command = "addRoleACL";
            cmd.rolename = rolename;
            cmd.acltype = acltype;
            cmd.topic = topic;
            cmd.allow = allow;
            return cmd;
        }

        public static Command addClientRole(String username, String rolename) {
            Command cmd = new Command();
            cmd.command = "addClientRole";
            cmd.username = username;
            cmd.rolename = rolename;
            return cmd;
        }
    }
}
