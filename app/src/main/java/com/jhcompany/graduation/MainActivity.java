package com.jhcompany.graduation;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.jhcompany.graduation.model.VehicleMessageModel;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TOPIC_PREFIX = "vehicle/";

    private String currentServerIP = ServerConstants.SERVER_FIRST;

    private EditText serverEditText;
    private EditText portEditText;

    private Button connectionButton;
    private Button disconnectionButton;
    private Button sendMessageButton;
    private TextView statusView;

    private String clientHandle;
    private ObjectMapper objectMapper;


    /**
     * ArrayAdapter to populate the list view
     */
    private List<Connection> connections = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        objectMapper = new ObjectMapper();
        serverEditText = (EditText) findViewById(R.id.server_edit_text);
        serverEditText.setText(currentServerIP);
        portEditText = (EditText) findViewById(R.id.port_edit_text);
        portEditText.setText(String.valueOf(ServerConstants.PORT_NUMBER));

        statusView = (TextView) findViewById(R.id.status_view);
        connectionButton = (Button) findViewById(R.id.connect_button);
        disconnectionButton = (Button) findViewById(R.id.disconnect_button);
        sendMessageButton = (Button) findViewById(R.id.send_message_button);
        connectionButton.setSelected(false);

        connectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectAction(createConnectionAction().getExtras());
            }
        });

        disconnectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // publish message
                sendMessage();
            }
        });
    }

    /**
     * Process data from the connect action
     *
     * @param data the {@link Bundle} returned by the Acitivty
     */
    private void connectAction(Bundle data) {
        MqttConnectOptions conOpt = new MqttConnectOptions();

        // The basic client information
        String server = (String) data.get(ActivityConstants.server);
        String clientId = (String) data.get(ActivityConstants.clientId);
        String portString = (String) data.get(ActivityConstants.port);
        boolean cleanSession = (Boolean) data.get(ActivityConstants.cleanSession);

        // 연결이 안되면 ip 를 바꿔서 connect 시도한다.
        if (GraduationApplication.shouldChangeServer()) {
            if (ServerConstants.SERVER_FIRST.equals(currentServerIP)) {
                currentServerIP = ServerConstants.SERVER_SECOND;
            } else {
                currentServerIP = ServerConstants.SERVER_FIRST;
            }
            serverEditText.setText(currentServerIP);
            GraduationApplication.resetRetryCount();
        }

        String uri = "tcp://" + currentServerIP + ":" + ServerConstants.PORT_NUMBER;
        MqttAndroidClient client = Connections.getInstance(this).createClient(this, uri, clientId);

        // create a client handle
        clientHandle = uri + clientId;

        // last will message
        String message = (String) data.get(ActivityConstants.message);
        String topic = (String) data.get(ActivityConstants.topic);
        Integer qos = (Integer) data.get(ActivityConstants.qos);
        Boolean retained = (Boolean) data.get(ActivityConstants.retained);

        // connection options
        String username = (String) data.get(ActivityConstants.username);
        String password = (String) data.get(ActivityConstants.password);

        int timeout = data.getInt(ActivityConstants.timeout);
        int keepalive = data.getInt(ActivityConstants.keepalive);

        Connection connection = new Connection(clientHandle, clientId, server, ServerConstants.PORT_NUMBER, this, client, false);
        connections.add(connection);

        // connect client
        String[] actionArgs = new String[1];
        actionArgs[0] = clientId;
        connection.changeConnectionStatus(Connection.ConnectionStatus.CONNECTING);

        conOpt.setCleanSession(cleanSession);
        conOpt.setConnectionTimeout(timeout);
        conOpt.setKeepAliveInterval(keepalive);
        if (!Strings.isNullOrEmpty(username)) {
            conOpt.setUserName(username);
        }
        if (!Strings.isNullOrEmpty(password)) {
            conOpt.setPassword(password.toCharArray());
        }

        final ActionListener callback = new ActionListener(this, ActionListener.Action.CONNECT, clientHandle, actionArgs);
        client.setCallback(new MqttCallbackHandler(this, clientHandle));

        connection.addConnectionOptions(conOpt);
        Connections.getInstance(this).addConnection(connection);
        try {
            client.connect(conOpt, null, callback);
        }
        catch (MqttException e) {
            Log.e(this.getClass().getCanonicalName(), "MqttException Occured", e);
        }
    }

    private Intent createConnectionAction() {
        String server = serverEditText.getText().toString();
        String port = portEditText.getText().toString();
        String clientId = Build.SERIAL;

        boolean cleanSession = false;

        Intent dataBundle = new Intent();
        //put data into a bundle to be passed back to ClientConnections
        dataBundle.putExtra(ActivityConstants.server, server);
        dataBundle.putExtra(ActivityConstants.port, port);
        dataBundle.putExtra(ActivityConstants.clientId, clientId);
        dataBundle.putExtra(ActivityConstants.action, ActivityConstants.connect);
        dataBundle.putExtra(ActivityConstants.cleanSession, cleanSession);

        // create a new bundle and put default advanced options into a bundle
        Bundle result = new Bundle();
        result.putString(ActivityConstants.message, ActivityConstants.empty);
        result.putString(ActivityConstants.topic, makeTopic());
        result.putInt(ActivityConstants.qos, ActivityConstants.defaultQos);
        result.putBoolean(ActivityConstants.retained, ActivityConstants.defaultRetained);

        result.putString(ActivityConstants.username, ActivityConstants.empty);
        result.putString(ActivityConstants.password, ActivityConstants.empty);

        result.putInt(ActivityConstants.timeout, ActivityConstants.defaultTimeOut);
        result.putInt(ActivityConstants.keepalive, ActivityConstants.defaultKeepAlive);
        result.putBoolean(ActivityConstants.ssl, ActivityConstants.defaultSsl);

        //add result bundle to the data being returned to ClientConnections
        dataBundle.putExtras(result);
        return dataBundle;
    }

    private void sendMessage() {
        if (Strings.isNullOrEmpty(clientHandle)) {
            Toast.makeText(this, "아직 Connection 이 맺어지지 않았습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        VehicleMessageModel messageModel = new VehicleMessageModel();
        messageModel.setTimestamp(TimestampUtils.getTimestamp());
        // TODO: 현재위치 가져오게 하기
        messageModel.setLatitude(30.1233);
        messageModel.setLongitude(42.1242);

        try {
            String message = objectMapper.writeValueAsString(messageModel);
            publish(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void publish(String message) {
        String topic = makeTopic();

        String[] args = new String[2];
        args[0] = message;
        args[1] = topic+";qos:"+0+";retained:"+false;

        try {
            Connections.getInstance(this)
                    .getConnection(clientHandle)
                    .getClient()
                    .publish(topic, message.getBytes(), 0, false, null, new ActionListener(MainActivity.this, ActionListener.Action.PUBLISH, clientHandle, args));
        } catch (MqttPersistenceException e) {
            Log.e(this.getClass().getCanonicalName(), "Failed to publish a messged from the client with the handle " + clientHandle, e);
        } catch (MqttException e) {
            Log.e(this.getClass().getCanonicalName(), "Failed to publish a messged from the client with the handle " + clientHandle, e);
        }
    }

    private String makeTopic() {
        return TOPIC_PREFIX + Build.SERIAL;
    }

    // TODO: disconnet, subscribe 구현하기
}
