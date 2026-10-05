package com.system_server.ai_demo.apps.master.user.controllers;

import com.system_server.ai_demo.apps.common.CommonLayout;
import com.system_server.ai_demo.apps.master.user.models.UserEditFailureType;
import com.system_server.ai_demo.apps.master.user.models.UserEditResult;
import com.system_server.ai_demo.apps.master.user.services.UserMasterService;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.database.entity.UsersEntity;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * ユーザマスタ管理画面コントローラー。一覧表示・CSV ダウンロード・CSV アップロード（編集）を行う。システム管理者のみ アクセス可能（認可は SecurityConfig
 * が強制）。共通レイアウトを使用する。
 */
@Controller
@CommonLayout
@RequestMapping("/master/users")
public class UserMasterCtrl {

    private static final String VIEW = "master/users";
    private static final String UPLOAD_FAILED_TITLE = "CSVアップロードに失敗しました";
    private static final String VALIDATION_ERROR_TITLE = "CSVの内容にエラーがあります。詳細は以下の通りです。";

    private final UserMasterService userMasterService;

    public UserMasterCtrl(UserMasterService userMasterService) {
        this.userMasterService = userMasterService;
    }

    /**
     * ユーザマスタ管理画面を表示する（全件・ユーザID昇順）。
     *
     * @param model モデル
     * @return ユーザマスタ管理画面テンプレート
     */
    @GetMapping
    public String showUsers(Model model) {
        addUsers(model);
        return VIEW;
    }

    /**
     * ユーザマスタの全件を CSV でダウンロードする。
     *
     * @param sort 並び替え対象列（userId／userName／email／departmentId／role。既定 userId）
     * @param order 並び順（asc／desc。既定 asc）
     * @return CSV ファイルのダウンロード応答
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam(defaultValue = "userId") String sort,
            @RequestParam(defaultValue = "asc") String order) {
        List<UsersEntity> users = new ArrayList<>(userMasterService.getAllUsers());
        users.sort(comparator(sort, order));
        byte[] csv = userMasterService.toCsv(users);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("ユーザマスタ.csv", StandardCharsets.UTF_8).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(disposition);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    /**
     * アップロードされた CSV でユーザマスタを編集し、結果に応じて一覧再表示またはエラー表示を行う。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @param principal ログイン中ユーザ（ユーザIDの取得に用いる）
     * @param model モデル
     * @return ユーザマスタ管理画面テンプレート
     */
    @PostMapping("/csv")
    public String uploadCsv(
            @RequestParam(value = "csvFile", required = false) MultipartFile csvFile,
            Principal principal,
            Model model) {
        if (csvFile == null || csvFile.isEmpty()) {
            model.addAttribute("errorTitle", UPLOAD_FAILED_TITLE);
            model.addAttribute("errorMessages", List.of(CheckMessages.REQUIRED_INPUT));
            addUsers(model);
            return VIEW;
        }
        UserEditResult result = userMasterService.edit(csvFile, principal.getName());
        if (!result.success()) {
            if (result.failureType() == UserEditFailureType.FORMAT) {
                model.addAttribute("errorTitle", UPLOAD_FAILED_TITLE);
            } else if (result.failureType() == UserEditFailureType.VALIDATION) {
                model.addAttribute("errorTitle", VALIDATION_ERROR_TITLE);
            } else if (result.failureType() == UserEditFailureType.BUSINESS) {
                model.addAttribute("errorTitle", result.errorTitle());
            }
            model.addAttribute("errorMessages", result.errors());
        }
        addUsers(model);
        return VIEW;
    }

    private void addUsers(Model model) {
        List<UsersEntity> users = userMasterService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("userCount", users.size());
    }

    private static Comparator<UsersEntity> comparator(String sort, String order) {
        Comparator<UsersEntity> comparator = switch (sort) {
            case "userName" -> Comparator.comparing(u -> u.getUserName());
            case "email" -> Comparator.comparing(u -> u.getEmail());
            case "departmentId" -> Comparator.comparing(u -> u.getDepartmentId());
            case "role" -> Comparator.comparing(u -> u.getRole());
            default -> Comparator.comparing(u -> u.getUserId());
        };
        return "desc".equalsIgnoreCase(order) ? comparator.reversed() : comparator;
    }
}
