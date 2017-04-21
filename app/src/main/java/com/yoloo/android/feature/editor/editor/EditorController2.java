package com.yoloo.android.feature.editor.editor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindColor;
import butterknife.BindDimen;
import butterknife.BindDrawable;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import com.airbnb.epoxy.EpoxyModel;
import com.annimon.stream.Stream;
import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bumptech.glide.Glide;
import com.github.jksiezni.permissive.Permissive;
import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.chip.Chip;
import com.hootsuite.nachos.terminator.ChipTerminatorHandler;
import com.hootsuite.nachos.validator.ChipifyingNachoValidator;
import com.sandrios.sandriosCamera.internal.configuration.CameraConfiguration;
import com.sandrios.sandriosCamera.internal.ui.camera.Camera1Activity;
import com.sandrios.sandriosCamera.internal.ui.camera2.Camera2Activity;
import com.sandrios.sandriosCamera.internal.utils.CameraHelper;
import com.yalantis.ucrop.UCrop;
import com.yoloo.android.R;
import com.yoloo.android.YolooApp;
import com.yoloo.android.data.model.GroupRealm;
import com.yoloo.android.data.model.MediaRealm;
import com.yoloo.android.data.model.PostRealm;
import com.yoloo.android.data.model.TagRealm;
import com.yoloo.android.data.repository.post.PostRepositoryProvider;
import com.yoloo.android.data.repository.tag.TagRepository;
import com.yoloo.android.data.repository.tag.datasource.TagDiskDataStore;
import com.yoloo.android.data.repository.tag.datasource.TagRemoteDataStore;
import com.yoloo.android.data.repository.user.UserRepositoryProvider;
import com.yoloo.android.feature.category.ChipAdapter;
import com.yoloo.android.feature.editor.EditorType;
import com.yoloo.android.feature.editor.selectbounty.BountySelectController;
import com.yoloo.android.feature.editor.selectgroup.SelectGroupController;
import com.yoloo.android.framework.MvpController;
import com.yoloo.android.ui.recyclerview.decoration.SpaceItemDecoration;
import com.yoloo.android.ui.widget.AutoCompleteTagAdapter;
import com.yoloo.android.util.BundleBuilder;
import com.yoloo.android.util.Connectivity;
import com.yoloo.android.util.ControllerUtil;
import com.yoloo.android.util.KeyboardUtil;
import com.yoloo.android.util.MediaUtil;
import com.yoloo.android.util.WeakHandler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import timber.log.Timber;

public class EditorController2 extends MvpController<EditorView, EditorPresenter>
    implements EditorView, ChipAdapter.OnItemSelectListener<TagRealm> {

  private static final String KEY_EDITOR_TYPE = "EDITOR_TYPE";

  private static final int REQUEST_SELECT_MEDIA = 1;
  private static final int REQUEST_CAPTURE_MEDIA = 2;

  private final WeakHandler handler = new WeakHandler();

  @BindView(R.id.toolbar) Toolbar toolbar;
  @BindView(R.id.iv_editor_post_image) ImageView ivEditorImage;
  @BindView(R.id.tv_editor_post_image_counter) TextView tvEditorImageCounter;
  @BindView(R.id.et_editor_post_content) EditText etEditorContent;
  @BindView(R.id.tv_editor_post_add_media) TextView tvEditorAddMedia;
  @BindView(R.id.tv_editor_post_select_group) TextView tvEditorSelectGroup;
  @BindView(R.id.et_editor_post_tags) NachoTextView etEditorTags;
  @BindView(R.id.recycler_view) RecyclerView rvEditorTrendingTags;
  @BindView(R.id.editor_post_add_bounty_wrapper) ViewGroup editorAddBountyWrapper;
  @BindView(R.id.tv_editor_post_add_bounty) TextView tvEditorAddBounty;

  @BindColor(R.color.primary) int primaryColor;
  @BindColor(R.color.primary_dark) int primaryDarkColor;

  @BindDrawable(R.drawable.dialog_tag_bg) Drawable tagBgDrawable;

  @BindDimen(R.dimen.spacing_micro) int microSpaceDimen;

  @BindString(R.string.label_editor_select_group) String selectGroupString;

  private List<Uri> imageUris = new ArrayList<>(5);

  private ChipAdapter<TagRealm> chipAdapter;
  private AutoCompleteTagAdapter tagAdapter;

  private PostRealm draft;

  private int editorType;

  private List<TagRealm> selectedTags;

  private int selectedTagCounter = 0;

  public EditorController2(@Nullable Bundle args) {
    super(args);
    setRetainViewMode(RetainViewMode.RETAIN_DETACH);
    setHasOptionsMenu(true);

    editorType = getArgs().getInt(KEY_EDITOR_TYPE);
  }

  public static EditorController2 create(@EditorType int editorType) {
    final Bundle bundle = new BundleBuilder().putInt(KEY_EDITOR_TYPE, editorType).build();

    return new EditorController2(bundle);
  }

  @Override
  protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    final int layoutRes = editorType == EditorType.ASK_QUESTION
        ? R.layout.controller_post_editor
        : R.layout.controller_editor_blog;

    return inflater.inflate(layoutRes, container, false);
  }

  @Override
  protected void onViewBound(@NonNull View view) {
    super.onViewBound(view);
    setupToolbar();
    setupRecyclerView();
    setupChipTextView();

    selectedTags = new ArrayList<>(7);

    ControllerUtil.preventDefaultBackPressAction(view, this::showDiscardDraftDialog);
  }

  @Override
  protected void onAttach(@NonNull View view) {
    super.onAttach(view);

    tagAdapter
        .getQuery()
        .filter(s -> !s.isEmpty())
        .filter(s -> s.length() > 2)
        .debounce(400, TimeUnit.MILLISECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(s -> getPresenter().searchTag(s));
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.menu_editor, menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    final int itemId = item.getItemId();

    KeyboardUtil.hideKeyboard(getView());

    if (itemId == android.R.id.home) {
      showDiscardDraftDialog();
    } else if (itemId == R.id.action_share && isValidToSend()) {
      setTempDraft();
      getPresenter().updateDraft(draft, EditorPresenter.NAV_SEND);
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_SELECT_MEDIA) {
      if (resultCode == Activity.RESULT_OK) {
        handleGalleryResult(data);
      }
    } else if (requestCode == REQUEST_CAPTURE_MEDIA) {
      if (resultCode == Activity.RESULT_OK) {
        handleCameraResult(data);
      }
    } else if (requestCode == UCrop.REQUEST_CROP) {
      if (resultCode == Activity.RESULT_OK) {
        handleCropResult(data);
      } else if (resultCode == UCrop.RESULT_ERROR) {
        handleCropError(data);
      }
    }
  }

  @NonNull
  @Override
  public EditorPresenter createPresenter() {
    return new EditorPresenter(
        TagRepository.getInstance(TagRemoteDataStore.getInstance(), TagDiskDataStore.getInstance()),
        PostRepositoryProvider.getRepository(), UserRepositoryProvider.getRepository());
  }

  @Override
  public void onDraftCreated(PostRealm draft) {
    this.draft = draft;
  }

  @Override
  public void onDraftUpdated(int navigation) {
    if (navigation == EditorPresenter.NAV_SEND) {
      getPresenter().sendPost();

      getRouter().handleBack();
    }
  }

  @Override
  public void onError(Throwable t) {
    Timber.e(t);
  }

  @Override
  public void onRecommendedTagsLoaded(List<TagRealm> tags) {
    chipAdapter.addChipItems(tags);
  }

  @Override
  public void onSearchTags(List<TagRealm> tags) {
    tagAdapter.replaceItems(tags);
    handler.post(etEditorTags::showDropDown);
  }

  @OnClick(R.id.tv_editor_post_add_media)
  void openAddMediaDialog() {
    new AlertDialog.Builder(getActivity())
        .setTitle(R.string.label_editor_select_media_source_title)
        .setItems(R.array.action_editor_list_media_source, (dialog, which) -> {
          KeyboardUtil.hideKeyboard(etEditorContent);

          if (which == 0) {
            checkGalleryPermissions();
          } else if (which == 1) {
            checkCameraPermissions();
          }
        })
        .show();
  }

  @OnClick(R.id.tv_editor_post_select_group)
  void openSelectGroupScreen() {
    Controller controller = SelectGroupController.create();
    controller.setTargetController(this);

    getRouter().pushController(RouterTransaction
        .with(controller)
        .pushChangeHandler(new VerticalChangeHandler())
        .popChangeHandler(new VerticalChangeHandler()));
  }

  @OnClick(R.id.editor_post_add_bounty_wrapper)
  void openAddBountyScreen() {
    if (Connectivity.isConnected(getApplicationContext())) {
      KeyboardUtil.hideKeyboard(etEditorContent);
      setTempDraft();

      getRouter().pushController(RouterTransaction
          .with(BountySelectController.create())
          .pushChangeHandler(new VerticalChangeHandler())
          .popChangeHandler(new VerticalChangeHandler()));
    } else {
      Snackbar.make(getView(), R.string.error_bounty_network, Snackbar.LENGTH_LONG).show();
    }
  }

  private void setTempDraft() {
    draft.setContent(etEditorContent.getText().toString());

    Stream
        .of(etEditorTags.getAllChips())
        .map(Chip::getText)
        .map(CharSequence::toString)
        .map(tagName -> new TagRealm().setName(tagName))
        .forEach(tag -> draft.addTag(tag));

    if (editorType == EditorType.ASK_QUESTION) {
      draft.setPostType(draft.getMedias().isEmpty() ? PostRealm.TYPE_TEXT : PostRealm.TYPE_RICH);
    } else if (editorType == EditorType.BLOG) {
      draft.setPostType(PostRealm.TYPE_BLOG);
    }
  }

  private void setupToolbar() {
    setSupportActionBar(toolbar);

    final ActionBar ab = getSupportActionBar();
    ab.setDisplayShowTitleEnabled(false);
    ab.setDisplayHomeAsUpEnabled(true);
  }

  private void openGallery() {
    Intent intent =
        new Intent(Intent.ACTION_GET_CONTENT, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    intent.setType("image/*");

    startActivityForResult(
        Intent.createChooser(intent, getResources().getString(R.string.label_select_media)),
        REQUEST_SELECT_MEDIA);
  }

  private void openCamera() {
    final Activity activity = getActivity();

    if (CameraHelper.hasCamera(activity)) {
      Intent cameraIntent = new Intent(activity,
          CameraHelper.hasCamera2(activity) ? Camera2Activity.class : Camera1Activity.class);

      cameraIntent.putExtra(CameraConfiguration.Arguments.REQUEST_CODE, REQUEST_CAPTURE_MEDIA);
      cameraIntent.putExtra(CameraConfiguration.Arguments.SHOW_PICKER, false);
      cameraIntent.putExtra(CameraConfiguration.Arguments.MEDIA_ACTION,
          CameraConfiguration.MEDIA_ACTION_PHOTO);
      cameraIntent.putExtra(CameraConfiguration.Arguments.ENABLE_CROP, false);

      startActivityForResult(cameraIntent, REQUEST_CAPTURE_MEDIA);
    }
  }

  private void handleGalleryResult(Intent data) {
    final Uri uri = data.getData();
    if (uri != null) {
      startCropActivity(uri);
    }
  }

  private void handleCameraResult(Intent data) {
    final String path = data.getStringExtra(CameraConfiguration.Arguments.FILE_PATH);
    if (path != null) {
      MediaUtil.addToPhoneGallery(path, getActivity());

      startCropActivity(Uri.fromFile(new File(path)));
    }
  }

  private void startCropActivity(Uri uri) {
    final Uri destUri =
        Uri.fromFile(new File(YolooApp.getCacheDirectory(), MediaUtil.createImageName()));

    Intent intent = UCrop
        .of(uri, destUri)
        .withAspectRatio(1, 1)
        .withMaxResultSize(800, 800)
        .withOptions(createUCropOptions())
        .getIntent(getActivity());

    startActivityForResult(intent, UCrop.REQUEST_CROP);
  }

  private void handleCropResult(Intent data) {
    final Uri uri = UCrop.getOutput(data);
    if (uri == null) {
      Toast.makeText(getActivity(), "Error occurred.", Toast.LENGTH_SHORT).show();
    } else {
      if (editorType == EditorType.ASK_QUESTION) {
        addThumbView(uri);
      } else if (editorType == EditorType.BLOG) {
        //addCoverView(uri);
      }
    }
  }

  private void handleCropError(Intent data) {
    Timber.e("Crop error: %s", UCrop.getError(data));
  }

  private void addThumbView(Uri uri) {
    ivEditorImage.setVisibility(View.VISIBLE);
    imageUris.add(uri);

    tvEditorImageCounter.setVisibility(imageUris.size() > 1 ? View.VISIBLE : View.GONE);

    Glide.with(getActivity()).load(uri).override(90, 90).into(ivEditorImage);

    MediaRealm media = new MediaRealm();
    media.setTempPath(uri.getPath());

    draft.addMedia(media);
  }

  private UCrop.Options createUCropOptions() {
    final UCrop.Options options = new UCrop.Options();
    options.setCompressionFormat(Bitmap.CompressFormat.WEBP);
    options.setCompressionQuality(85);
    options.setToolbarColor(primaryColor);
    options.setStatusBarColor(primaryDarkColor);
    options.setToolbarTitle(getResources().getString(R.string.label_editor_crop_image_title));
    return options;
  }

  private void checkCameraPermissions() {
    new Permissive.Request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO)
        .whenPermissionsGranted(permissions -> openCamera())
        .whenPermissionsRefused(permissions -> Snackbar
            .make(getView(), "Permission is denied!", Snackbar.LENGTH_SHORT)
            .show())
        .execute(getActivity());
  }

  private void checkGalleryPermissions() {
    new Permissive.Request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .whenPermissionsGranted(permissions -> openGallery())
        .whenPermissionsRefused(permissions -> Snackbar
            .make(getView(), "Permission is denied!", Snackbar.LENGTH_SHORT)
            .show())
        .execute(getActivity());
  }

  private void showDiscardDraftDialog() {
    new AlertDialog.Builder(getActivity())
        .setTitle(R.string.action_catalog_discard_draft)
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          getPresenter().deleteDraft();
          getRouter().popToRoot();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  @Override
  public void onItemSelect(View v, EpoxyModel<?> model, TagRealm item, boolean selected) {
    if (selectedTags.size() == 7) {
      Toast.makeText(getActivity(), "You have selected 7 tags", Toast.LENGTH_SHORT).show();
      return;
    }

    if (selectedTags.contains(item)) {
      selectedTags.remove(item);
      --selectedTagCounter;
    } else {
      selectedTags.add(item);
      List<String> chipValues = etEditorTags.getChipValues();
      chipValues.add(chipValues.size(), item.getName());
      etEditorTags.chipifyAllUnterminatedTokens();

      ++selectedTagCounter;
    }
  }

  private void setupRecyclerView() {
    chipAdapter = new ChipAdapter<>(this);
    chipAdapter.setBackgroundDrawable(R.drawable.provider_tag_bg);

    rvEditorTrendingTags.setLayoutManager(
        new LinearLayoutManager(getActivity(), OrientationHelper.HORIZONTAL, false));
    rvEditorTrendingTags.setItemAnimator(new DefaultItemAnimator());
    rvEditorTrendingTags.addItemDecoration(
        new SpaceItemDecoration(microSpaceDimen, SpaceItemDecoration.HORIZONTAL));
    rvEditorTrendingTags.setHasFixedSize(true);
    rvEditorTrendingTags.setAdapter(chipAdapter);
  }

  private void setupChipTextView() {
    tagAdapter = new AutoCompleteTagAdapter(getActivity());

    etEditorTags.setAdapter(tagAdapter);
    etEditorTags.setIllegalCharacters('\"', '.', '~');
    etEditorTags.addChipTerminator('\n', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
    etEditorTags.addChipTerminator(' ', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_TO_TERMINATOR);
    etEditorTags.addChipTerminator(';', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_CURRENT_TOKEN);
    etEditorTags.setNachoValidator(new ChipifyingNachoValidator());
    etEditorTags.enableEditChipOnTouch(true, true);
    etEditorTags.setOnChipClickListener((chip, motionEvent) -> {

    });
  }

  public void onGroupSelected(GroupRealm group) {
    draft.setGroupId(group.getId());
    tvEditorSelectGroup.setText(group.getName());
  }

  /*private void showDropdown() {
    final int tagSize = etEditorTags.getAllChips().size();
    if (tagSize <= 7) {
      tagDropdownRunnable = etEditorTags::showDropDown;
    }
  }*/

  private void showMessage(String message) {
    Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
  }

  private boolean isValidToSend() {
    if (ivEditorImage.getDrawable() == null) {
      if (etEditorContent.getText().toString().isEmpty()) {
        showMessage("You must add text or image to post!");
        return false;
      }

      if (tvEditorSelectGroup.getText().equals(selectGroupString)) {
        showMessage("Select a group!");
        return false;
      }

      if (etEditorTags.getChipValues().isEmpty()) {
        showMessage("Tags are empty!");
        return false;
      }
    } else {
      if (tvEditorSelectGroup.getText().equals(selectGroupString)) {
        showMessage("Select a group!");
        return false;
      }

      if (etEditorTags.getChipValues().isEmpty()) {
        showMessage("Tags are empty!");
        return false;
      }
    }

    return true;
  }
}
