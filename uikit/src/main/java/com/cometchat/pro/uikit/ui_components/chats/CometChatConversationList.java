package com.cometchat.pro.uikit.ui_components.chats;


import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cometchat.pro.constants.CometChatConstants;
import com.cometchat.pro.core.CometChat;
import com.cometchat.pro.core.ConversationsRequest;
import com.cometchat.pro.exceptions.CometChatException;
import com.cometchat.pro.helpers.CometChatHelper;
import com.cometchat.pro.models.Action;
import com.cometchat.pro.models.Group;
import com.cometchat.pro.models.MessageReceipt;
import com.cometchat.pro.models.TypingIndicator;
import com.cometchat.pro.models.User;
import com.cometchat.pro.uikit.ui_components.shared.CometChatSnackBar;
import com.cometchat.pro.uikit.ui_components.shared.cometchatConversations.CometChatConversations;
import com.cometchat.pro.uikit.R;
import com.cometchat.pro.models.BaseMessage;
import com.cometchat.pro.models.Conversation;
import com.cometchat.pro.models.CustomMessage;
import com.cometchat.pro.models.MediaMessage;
import com.cometchat.pro.models.TextMessage;
import com.cometchat.pro.uikit.ui_resources.utils.CometChatError;
import com.cometchat.pro.uikit.ui_resources.utils.custom_alertDialog.CustomAlertDialogHelper;
import com.cometchat.pro.uikit.ui_resources.utils.custom_alertDialog.OnAlertDialogButtonClickListener;
import com.cometchat.pro.uikit.ui_resources.utils.recycler_touch.RecyclerViewSwipeListener;
import com.cometchat.pro.uikit.ui_settings.FeatureRestriction;
import com.cometchat.pro.uikit.ui_settings.UIKitSettings;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;

import com.cometchat.pro.uikit.ui_resources.utils.item_clickListener.OnItemClickListener;
import com.cometchat.pro.uikit.ui_resources.utils.FontUtils;
import com.cometchat.pro.uikit.ui_resources.utils.Utils;

/*

* Purpose - CometChatConversationList class is a fragment used to display list of conversations and perform certain action on click of item.
            It also provide search bar to perform search operation on the list of conversations.User can search by username, groupname, last message of conversation.

* Created on - 20th December 2019

* Modified on  - 23rd March 2020

*/

public class CometChatConversationList extends Fragment implements TextWatcher, OnAlertDialogButtonClickListener {

    private CometChatConversations rvConversationList;    //Uses to display list of conversations.

    private ConversationsRequest conversationsRequest;    //Uses to fetch Conversations.

    private String conversationListType = UIKitSettings.getConversationsMode().toString();

    private static OnItemClickListener events;

    private EditText searchEdit;    //Uses to perform search operations.

    private TextView tvTitle;

    private ShimmerFrameLayout conversationShimmer;

    private RelativeLayout rlSearchBox;

    private LinearLayout noConversationView;

    private static final String TAG = "ConversationList";

    private View view;

    private List<Conversation> conversationList = new ArrayList<>();

    private ImageView startConversation;

    public CometChatConversationList() {
        // Required empty public constructor
    }

    private ProgressDialog progressDialog;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_cometchat_conversationlist, container, false);

        rvConversationList = view.findViewById(R.id.rv_conversation_list);

        noConversationView = view.findViewById(R.id.no_conversation_view);

        searchEdit = view.findViewById(R.id.search_bar);

        tvTitle = view.findViewById(R.id.tv_title);

        tvTitle.setTypeface(FontUtils.getInstance(getActivity()).getTypeFace(FontUtils.robotoMedium));

        rlSearchBox = view.findViewById(R.id.rl_search_box);

        conversationShimmer = view.findViewById(R.id.shimmer_layout);

        checkDarkMode();

        CometChatError.init(getContext());

        startConversation = view.findViewById(R.id.start_conversation);
        FeatureRestriction.isStartConversationEnabled(new FeatureRestriction.OnSuccessListener() {
            @Override
            public void onSuccess(Boolean booleanVal) {
                if (booleanVal)
                    startConversation.setVisibility(View.VISIBLE);
                else
                    startConversation.setVisibility(View.GONE);
            }
        });

        startConversation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CometChatStartConversation.launch(getContext());
            }
        });
        searchEdit.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                if (!textView.getText().toString().isEmpty()) {
                    progressDialog = ProgressDialog.show(getContext(),"",getString(R.string.search));
                    refreshConversation(new CometChat.CallbackListener<List<Conversation>>() {
                        @Override
                        public void onSuccess(List<Conversation> conversationList) {
                            rvConversationList.searchConversation(textView.getText().toString(), new Filter.FilterListener() {
                                @Override
                                public void onFilterComplete(int i) {
                                    if (i==0) {
                                        searchConversation(textView.getText().toString());
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(CometChatException e) {
                            if (progressDialog!=null)
                                progressDialog.dismiss();
                            CometChatSnackBar.show(getContext(),rvConversationList,
                                    CometChatError.localized(e),CometChatSnackBar.ERROR);
                        }
                    });
                }
                return true;
            }
            return false;
        });

        // Uses to fetch next list of conversations if rvConversationList (RecyclerView) is scrolled in upward direction.
        rvConversationList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {

                if (!recyclerView.canScrollVertically(1)) {
                    makeConversationList();
                }

            }
        });

        // Used to trigger event on click of conversation item in rvConversationList (RecyclerView)
        rvConversationList.setItemClickListener(new OnItemClickListener<Conversation>() {
            @Override
            public void OnItemClick(Conversation conversation, int position) {
                if (events!=null)
                    events.OnItemClick(conversation,position);
            }
        });

        RecyclerViewSwipeListener swipeHelper = new RecyclerViewSwipeListener(getContext()) {
            @Override
            public void instantiateUnderlayButton(RecyclerView.ViewHolder viewHolder, List<UnderlayButton> underlayButtons) {
                Bitmap deleteBitmap = Utils.drawableToBitmap(getResources().getDrawable(R.drawable.ic_delete_conversation));
                FeatureRestriction.isDeleteConversationEnabled(new FeatureRestriction.OnSuccessListener() {
                    @Override
                    public void onSuccess(Boolean booleanVal) {
                        if (booleanVal) {
                            underlayButtons.add(new RecyclerViewSwipeListener.UnderlayButton(
                                    "Delete",
                                    deleteBitmap,
                                    getResources().getColor(R.color.red),
                                    new RecyclerViewSwipeListener.UnderlayButtonClickListener() {
                                        @Override
                                        public void onClick(final int pos) {
                                            Conversation conversation = rvConversationList.getConversation(pos);
                                            if (conversation!=null) {
                                                String conversationUid = "";
                                                String type = "";
                                                if (conversation.getConversationType()
                                                        .equalsIgnoreCase(CometChatConstants.CONVERSATION_TYPE_GROUP)) {
                                                    conversationUid = ((Group)conversation.getConversationWith()).getGuid();
                                                    type = CometChatConstants.CONVERSATION_TYPE_GROUP;
                                                } else {
                                                    conversationUid = ((User)conversation.getConversationWith()).getUid();
                                                    type = CometChatConstants.CONVERSATION_TYPE_USER;
                                                }
                                                String finalConversationUid = conversationUid;
                                                String finalType = type;
                                                new CustomAlertDialogHelper(getContext(),
                                                        getString(R.string.delete_conversation_message),
                                                        null,
                                                        getString(R.string.yes),
                                                        "", getString(R.string.no), new OnAlertDialogButtonClickListener() {
                                                    @Override
                                                    public void onButtonClick(AlertDialog alertDialog, View v, int which, int popupId) {
                                                        if (which==DialogInterface.BUTTON_POSITIVE) {
                                                            ProgressDialog progressDialog = ProgressDialog.show(getContext(),null,
                                                                    getString(R.string.deleting_conversation));
                                                            CometChat.deleteConversation(
                                                                    finalConversationUid, finalType,
                                                                    new CometChat.CallbackListener<String>() {
                                                                        @Override
                                                                        public void onSuccess(String s) {
                                                                            Handler handler = new Handler();
                                                                            handler.postDelayed(new Runnable() {
                                                                                public void run() {
                                                                                    alertDialog.dismiss();
                                                                                    progressDialog.dismiss();
                                                                                }
                                                                            }, 1500);
                                                                            rvConversationList.remove(conversation);
                                                                        }

                                                                        @Override
                                                                        public void onError(CometChatException e) {
                                                                            progressDialog.dismiss();
                                                                            e.printStackTrace();
                                                                        }
                                                                    });
                                                        } else if (which==DialogInterface.BUTTON_NEGATIVE) {
                                                            alertDialog.dismiss();
                                                        }
                                                    }
                                                }, 1, true);

                                            }
                                        }
                                    }
                            ));
                        }
                    }
                });
            }
        };
        swipeHelper.attachToRecyclerView(rvConversationList);
        return view;
    }

    private void searchConversation(String str) {
        conversationsRequest.fetchNext(new CometChat.CallbackListener<List<Conversation>>() {
            @Override
            public void onSuccess(List<Conversation> conversations) {
                conversationList.addAll(conversations);
                if (conversations.size() != 0) {
                    rvConversationList.setConversationList(conversations);
                    searchConversation(str);
                } else {
                    rvConversationList.searchConversation(str, new Filter.FilterListener() {
                        @Override
                        public void onFilterComplete(int i) {
                            if (progressDialog!=null)
                                progressDialog.dismiss();
                        }
                    });
                }
            }

            @Override
            public void onError(CometChatException e) {
                e.printStackTrace();
            }
        });
    }

    public void refreshConversation(CometChat.CallbackListener callbackListener) {
        rvConversationList.clearList();
        conversationList.clear();
        conversationsRequest = null;
        if (conversationsRequest == null) {
            conversationsRequest = new ConversationsRequest.ConversationsRequestBuilder().setLimit(50).build();
            if (conversationListType!=null)
                conversationsRequest = new ConversationsRequest.ConversationsRequestBuilder()
                        .setConversationType(conversationListType).setLimit(50).build();
        }
        conversationsRequest.fetchNext(new CometChat.CallbackListener<List<Conversation>>() {
            @Override
            public void onSuccess(List<Conversation> conversations) {
                conversationList.addAll(conversations);
                if (conversationList.size() != 0) {
                    stopHideShimmer();
                    noConversationView.setVisibility(View.GONE);
                    rvConversationList.setConversationList(conversations);
                } else {
                    checkNoConverstaion();
                }
                callbackListener.onSuccess(conversationList);
            }

            @Override
            public void onError(CometChatException e) {
                stopHideShimmer();
                if (getActivity()!=null)
                    CometChatSnackBar.show(getContext(),rvConversationList,
                            CometChatError.localized(e),CometChatSnackBar.ERROR);
                Log.d(TAG, "onError: "+e.getMessage());
                callbackListener.onError(e);
            }
        });
    }
    private void checkDarkMode() {
        if(Utils.isDarkMode(getContext())) {
            tvTitle.setTextColor(getResources().getColor(R.color.textColorWhite));
        } else {
            tvTitle.setTextColor(getResources().getColor(R.color.primaryTextColor));
        }
    }


    public void setConversationListType(String conversationListType) {
        this.conversationListType = conversationListType;
    }
    /**
     * This method is used to retrieve list of conversations you have done.
     * For more detail please visit our official documentation {@link "https://prodocs.cometchat.com/docs/android-messaging-retrieve-conversations" }
     *
     * @see ConversationsRequest
     */
    private void makeConversationList() {

        if (conversationsRequest == null) {
            conversationsRequest = new ConversationsRequest.ConversationsRequestBuilder().setLimit(50).build();
            if (conversationListType!=null)
                conversationsRequest = new ConversationsRequest.ConversationsRequestBuilder()
                        .setConversationType(conversationListType).setLimit(50).build();
        }
        conversationsRequest.fetchNext(new CometChat.CallbackListener<List<Conversation>>() {
            @Override
            public void onSuccess(List<Conversation> conversations) {
                conversationList.addAll(conversations);
                if (conversationList.size() != 0) {
                    stopHideShimmer();
                    noConversationView.setVisibility(View.GONE);
                    rvConversationList.setConversationList(conversations);
                } else {
                    checkNoConverstaion();
                }
            }

            @Override
            public void onError(CometChatException e) {
                stopHideShimmer();
                if (getActivity()!=null)
                    CometChatSnackBar.show(getContext(),rvConversationList,
                            getString(R.string.err_default_message),CometChatSnackBar.ERROR);
                Log.d(TAG, "onError: "+e.getMessage());
            }
        });
    }

    private void checkNoConverstaion() {
        if (rvConversationList.size()==0) {
            stopHideShimmer();
            noConversationView.setVisibility(View.VISIBLE);
            rvConversationList.setVisibility(View.GONE);
        } else {
            noConversationView.setVisibility(View.GONE);
            rvConversationList.setVisibility(View.VISIBLE);
        }
    }

    /**
     * This method is used to hide shimmer effect if the list is loaded.
     */
    private void stopHideShimmer() {
        conversationShimmer.stopShimmer();
        conversationShimmer.setVisibility(View.GONE);
        tvTitle.setVisibility(View.VISIBLE);
        rlSearchBox.setVisibility(View.VISIBLE);
    }


    /**
     * @param onItemClickListener An object of <code>OnItemClickListener&lt;T&gt;</code> abstract class helps to initialize with events
     *                            to perform onItemClick & onItemLongClick.
     * @see OnItemClickListener
     */
    public static void setItemClickListener(OnItemClickListener<Conversation> onItemClickListener) {
        events = onItemClickListener;
    }

    /**
     * This method has message listener which recieve real time message and based on these messages, conversations are updated.
     *
     * @see CometChat#addMessageListener(String, CometChat.MessageListener)
     */
    private void addConversationListener() {
        CometChat.addMessageListener(TAG, new CometChat.MessageListener() {
            @Override
            public void onTextMessageReceived(TextMessage message) {
                if (rvConversationList!=null) {
                    rvConversationList.refreshConversation(message);
                    checkNoConverstaion();
                }
            }

            @Override
            public void onMediaMessageReceived(MediaMessage message) {
                if (rvConversationList != null) {
                    rvConversationList.refreshConversation(message);
                    checkNoConverstaion();
                }
            }

            @Override
            public void onCustomMessageReceived(CustomMessage message) {
                if (rvConversationList != null) {
                    rvConversationList.refreshConversation(message);
                    checkNoConverstaion();
                }
            }

            @Override
            public void onMessagesDelivered(MessageReceipt messageReceipt) {
                if (rvConversationList!=null)
                    rvConversationList.setReciept(messageReceipt);
            }

            @Override
            public void onMessagesRead(MessageReceipt messageReceipt) {
                if (rvConversationList!=null)
                    rvConversationList.setReciept(messageReceipt);
            }

            @Override
            public void onMessageEdited(BaseMessage message) {
                if (rvConversationList!=null)
                    rvConversationList.refreshConversation(message);
            }

            @Override
            public void onMessageDeleted(BaseMessage message) {
                if (rvConversationList!=null)
                    rvConversationList.refreshConversation(message);
            }

            @Override
            public void onTypingStarted(TypingIndicator typingIndicator) {
                if (rvConversationList!=null)
                    rvConversationList.setTypingIndicator(typingIndicator,false);
            }

            @Override
            public void onTypingEnded(TypingIndicator typingIndicator) {
                if (rvConversationList!=null)
                    rvConversationList.setTypingIndicator(typingIndicator,true);
            }
        });
        CometChat.addGroupListener(TAG, new CometChat.GroupListener() {
            @Override
            public void onGroupMemberKicked(Action action, User kickedUser, User kickedBy, Group kickedFrom) {
                Log.e(TAG, "onGroupMemberKicked: "+kickedUser);
                if (kickedUser.getUid().equals(CometChat.getLoggedInUser().getUid())) {
                    if (rvConversationList!=null)
                        updateConversation(action,true);
                }
                else {
                    updateConversation(action,false);
                }
            }

            @Override
            public void onMemberAddedToGroup(Action action, User addedby, User userAdded, Group addedTo) {
                Log.e(TAG, "onMemberAddedToGroup: " );
                updateConversation(action,false);
            }

            @Override
            public void onGroupMemberJoined(Action action, User joinedUser, Group joinedGroup) {
                Log.e(TAG, "onGroupMemberJoined: " );
                updateConversation(action,false);
            }

            @Override
            public void onGroupMemberLeft(Action action, User leftUser, Group leftGroup) {
                Log.e(TAG, "onGroupMemberLeft: " );
                if (leftUser.getUid().equals(CometChat.getLoggedInUser().getUid())) {
                    updateConversation(action,true);
                }
                else {
                    updateConversation(action,false);
                }
            }

            @Override
            public void onGroupMemberScopeChanged(Action action, User updatedBy, User updatedUser, String scopeChangedTo, String scopeChangedFrom, Group group) {
                updateConversation(action,false);
            }
        });
    }

    /**
     * This method is used to update conversation received in real-time.
     * @param baseMessage is object of BaseMessage.class used to get respective Conversation.
     * @param isRemove is boolean used to check whether conversation needs to be removed or not.
     *
     * @see CometChatHelper#getConversationFromMessage(BaseMessage) This method return the conversation
     * of receiver using baseMessage.
     *
     */
    private void updateConversation(BaseMessage baseMessage,boolean isRemove) {
        if (rvConversationList != null) {
            Conversation conversation = CometChatHelper.getConversationFromMessage(baseMessage);
            if (isRemove)
                rvConversationList.remove(conversation);
            else
                rvConversationList.update(conversation);
            checkNoConverstaion();
        }
    }

    /**
     * This method is used to remove the conversationlistener.
     */
    private void removeConversationListener() {
        CometChat.removeMessageListener(TAG);
        CometChat.removeGroupListener(TAG);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        conversationsRequest = null;
        searchEdit.addTextChangedListener(this);
        rvConversationList.clearList();
        makeConversationList();
        addConversationListener();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: ");
        searchEdit.removeTextChangedListener(this);
        removeConversationListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: ");
        removeConversationListener();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (s.length() == 0) {
//                    // if searchEdit is empty then fetch all conversations.
            conversationsRequest = null;
            rvConversationList.clearList();
            makeConversationList();
        }
    }

    @Override
    public void onButtonClick(AlertDialog alertDialog, View v, int which, int popupId) {
        if (which== DialogInterface.BUTTON_NEGATIVE)
            alertDialog.dismiss();
        else if (which==DialogInterface.BUTTON_POSITIVE) {

        }
    }
}
