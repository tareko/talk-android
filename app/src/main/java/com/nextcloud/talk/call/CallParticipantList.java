/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to keep track of the participants in a call based on the signaling messages.
 * <p>
 * The CallParticipantList adds a listener for participant list messages as soon as it is created and starts tracking
 * the call participants until destroyed. Notifications about the changes can be received by adding an observer to the
 * CallParticipantList; note that no sorting is guaranteed on the participants.
 */
public class CallParticipantList {

    private final CallParticipantListNotifier callParticipantListNotifier = new CallParticipantListNotifier();

    private final SignalingMessageReceiver signalingMessageReceiver;

    public interface Observer {
        void onCallParticipantsChanged(Collection<Participant> joined, Collection<Participant> updated,
                                       Collection<Participant> left, Collection<Participant> unchanged);
        void onCallEndedForAll();
    }

    private final SignalingMessageReceiver.ParticipantListMessageListener participantListMessageListener =
            new SignalingMessageReceiver.ParticipantListMessageListener() {

        private final Map<String, Participant> callParticipants = new HashMap<>();

        @Override
        public void onUsersInRoom(List<Participant> participants) {
            processParticipantList(participants, true);
        }

        @Override
        public void onParticipantsUpdate(List<Participant> participants) {
            processParticipantList(participants, false);
        }

        private void processParticipantList(List<Participant> participants, boolean isFullList) {
            Collection<Participant> joined = new ArrayList<>();
            Collection<Participant> updated = new ArrayList<>();
            Collection<Participant> left = new ArrayList<>();
            Collection<Participant> unchanged = new ArrayList<>();

            Map<String, Participant> knownBySessionAlias = new HashMap<>();
            Map<String, Participant> knownByActor = new HashMap<>();
            Map<String, Participant> knownByUser = new HashMap<>();

            // Build lookup tables so aggregated updates that reference alternate identifiers can be matched to the
            // already tracked participant instead of being treated as brand-new joins.
            for (Participant existingParticipant : callParticipants.values()) {
                ArrayList<String> existingSessionIds = existingParticipant.getSessionIds();
                if (existingSessionIds != null) {
                    for (String existingSessionId : existingSessionIds) {
                        if (existingSessionId != null) {
                            knownBySessionAlias.put(existingSessionId, existingParticipant);
                        }
                    }
                }

                String actorKey = buildActorKey(existingParticipant.getActorType(), existingParticipant.getActorId());
                if (actorKey != null) {
                    knownByActor.put(actorKey, existingParticipant);
                }

                String userKey = buildUserKey(existingParticipant.getUserId());
                if (userKey != null) {
                    knownByUser.put(userKey, existingParticipant);
                }
            }

            Collection<Participant> knownCallParticipantsNotFound = new ArrayList<>(callParticipants.values());

            for (Participant participant : participants) {
                String sessionId = participant.getSessionId();
                Participant callParticipant = callParticipants.get(sessionId);
                boolean aliasMatch = false;

                if (callParticipant == null) {
                    callParticipant = knownBySessionAlias.get(sessionId);
                    if (callParticipant != null) {
                        aliasMatch = sessionId != null && !sessionId.equals(callParticipant.getSessionId());
                    }
                }

                if (callParticipant == null) {
                    String actorKey = buildActorKey(participant.getActorType(), participant.getActorId());
                    if (actorKey != null) {
                        callParticipant = knownByActor.get(actorKey);
                        if (callParticipant != null) {
                            aliasMatch = sessionId != null && !sessionId.equals(callParticipant.getSessionId());
                        }
                    }
                }

                if (callParticipant == null) {
                    String userKey = buildUserKey(participant.getUserId());
                    if (userKey != null) {
                        callParticipant = knownByUser.get(userKey);
                        if (callParticipant != null) {
                            aliasMatch = sessionId != null && !sessionId.equals(callParticipant.getSessionId());
                        }
                    }
                }

                boolean knownCallParticipant = callParticipant != null;
                if (!knownCallParticipant && participant.getInCall() != Participant.InCallFlags.DISCONNECTED) {
                    Participant participantCopy = copyParticipant(participant);
                    callParticipants.put(sessionId, participantCopy);
                    joined.add(copyParticipant(participant));
                } else if (!knownCallParticipant) {
                    // Ignore disconnect updates for unknown participants.
                } else if (!aliasMatch && participant.getInCall() == Participant.InCallFlags.DISCONNECTED) {
                    callParticipants.remove(callParticipant.getSessionId());
                    // No need to copy it, as it will be no longer used.
                    callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
                    left.add(callParticipant);
                } else if (aliasMatch && participant.getInCall() == Participant.InCallFlags.DISCONNECTED) {
                    // Aggregated aliases may report DISCONNECTED for secondary sessions; keep the stored
                    // participant untouched but refresh known identifiers.
                    callParticipant.setSessionIds(copySessionIds(participant));
                    callParticipant.setDisplayName(participant.getDisplayName());
                } else {
                    long incomingInCall = participant.getInCall();
                    boolean inCallChanged = callParticipant.getInCall() != incomingInCall;
                    callParticipant.setInCall(incomingInCall);
                    callParticipant.setSessionIds(copySessionIds(participant));
                    callParticipant.setDisplayName(participant.getDisplayName());

                    if (inCallChanged) {
                        updated.add(copyParticipant(callParticipant));
                    } else {
                        unchanged.add(copyParticipant(callParticipant));
                    }
                }

                if (knownCallParticipant) {
                    knownCallParticipantsNotFound.remove(callParticipant);
                }
            }

            if (isFullList) {
                // Incremental updates only include the changed participants; keep the existing snapshot when they
                // are missing and rely on explicit DISCONNECTED flags to signal that someone left the call.
                for (Participant callParticipant : knownCallParticipantsNotFound) {
                    callParticipants.remove(callParticipant.getSessionId());
                    // No need to copy it, as it will be no longer used.
                    callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
                }
                left.addAll(knownCallParticipantsNotFound);
            }

            if (!joined.isEmpty() || !updated.isEmpty() || !left.isEmpty()) {
                callParticipantListNotifier.notifyChanged(joined, updated, left, unchanged);
            }
        }

        @Override
        public void onAllParticipantsUpdate(long inCall) {
            if (inCall != Participant.InCallFlags.DISCONNECTED) {
                // Updating all participants is expected to happen only to disconnect them.
                return;
            }

            callParticipantListNotifier.notifyCallEndedForAll();

            Collection<Participant> joined = new ArrayList<>();
            Collection<Participant> updated = new ArrayList<>();
            Collection<Participant> left = new ArrayList<>(callParticipants.size());
            Collection<Participant> unchanged = new ArrayList<>();

            for (Participant callParticipant : callParticipants.values()) {
                // No need to copy it, as it will be no longer used.
                callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
                left.add(callParticipant);
            }
            callParticipants.clear();

            if (!left.isEmpty()) {
                callParticipantListNotifier.notifyChanged(joined, updated, left, unchanged);
            }
        }

        private Participant copyParticipant(Participant participant) {
            Participant copiedParticipant = new Participant();
            copiedParticipant.setActorId(participant.getActorId());
            copiedParticipant.setActorType(participant.getActorType());
            copiedParticipant.setInCall(participant.getInCall());
            copiedParticipant.setInternal(participant.getInternal());
            copiedParticipant.setLastPing(participant.getLastPing());
            copiedParticipant.setSessionId(participant.getSessionId());
            copiedParticipant.setSessionIds(copySessionIds(participant));
            copiedParticipant.setType(participant.getType());
            copiedParticipant.setUserId(participant.getUserId());
            copiedParticipant.setDisplayName(participant.getDisplayName());

            return copiedParticipant;
        }

        private ArrayList<String> copySessionIds(Participant participant) {
            ArrayList<String> sessionIds = participant.getSessionIds();
            if (sessionIds == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(sessionIds);
        }

        private String buildActorKey(Participant.ActorType actorType, String actorId) {
            if (actorType == null || actorId == null) {
                return null;
            }
            return actorType.name() + ":" + actorId;
        }

        private String buildUserKey(String userId) {
            if (userId == null) {
                return null;
            }
            return "user:" + userId;
        }
    };

    public CallParticipantList(SignalingMessageReceiver signalingMessageReceiver) {
        this.signalingMessageReceiver = signalingMessageReceiver;
        this.signalingMessageReceiver.addListener(participantListMessageListener);
    }

    public void destroy() {
        signalingMessageReceiver.removeListener(participantListMessageListener);
    }

    public void addObserver(Observer observer) {
        callParticipantListNotifier.addObserver(observer);
    }

    public void removeObserver(Observer observer) {
        callParticipantListNotifier.removeObserver(observer);
    }
}
