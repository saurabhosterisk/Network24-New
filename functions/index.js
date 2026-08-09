const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

admin.initializeApp();

setGlobalOptions({ region: "us-central1" });

exports.sendAlertOnChannelDown = onDocumentCreated(
  "rooms/channel_down/messages/{messageId}",
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("No snapshot data");
      return;
    }

    const msg = snap.data() || {};
    const text = msg.text || "Channel down alert!";
    const senderName = msg.senderName || "System";

    logger.info("Trigger fired for channel_down message", { senderName, text });

    const payload = {
      notification: {
        title: `🚨 Alert from ${senderName}`,
        body: text,
      },
      data: { roomId: "channel_down" },
    };

    try {
      const res = await admin.messaging().sendToTopic("channel_down_alerts", payload);
      logger.info("Push sent", res);
    } catch (e) {
      logger.error("Push failed", e);
    }
  }
);
