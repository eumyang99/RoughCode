package com.cody.roughcode.mypage.service;

import com.cody.roughcode.code.dto.res.CodeInfoRes;
import com.cody.roughcode.code.entity.CodeLikes;
import com.cody.roughcode.code.entity.CodeSelectedTags;
import com.cody.roughcode.code.entity.Codes;
import com.cody.roughcode.code.repository.*;
import com.cody.roughcode.project.dto.res.ProjectInfoRes;
import com.cody.roughcode.project.entity.ProjectSelectedTags;
import com.cody.roughcode.project.entity.Projects;
import com.cody.roughcode.project.repository.FeedbacksRepository;
import com.cody.roughcode.project.repository.ProjectsRepository;
import com.cody.roughcode.project.repository.SelectedFeedbacksRepository;
import com.cody.roughcode.user.entity.Users;
import com.cody.roughcode.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MypageServiceImpl implements MypageService{
    private final ProjectsRepository projectsRepository;
    private final CodesRepository codesRepository;
    private final CodeSelectedTagsRepository codeSelectedTagsRepository;
    private final CodeLikesRepository codeLikesRepository;
    private final UsersRepository usersRepository;
    private final FeedbacksRepository feedbackRepository;
    private final ReviewsRepository reviewsRepository;
    private final SelectedFeedbacksRepository selectedFeedbacksRepository;
    private final SelectedReviewsRepository selectedReviewsRepository;

    private void findUser(Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
    }

    @Value("${stat-card.filepath}")
    static String statFilePath;

    @Override
    public String makeStatCard(String userName) throws FileNotFoundException {
        Users user = usersRepository.findByName(userName).orElse(null);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        HashMap<String, Integer> stats = new HashMap<>();

        int res = 0;

        // 프로젝트 피드백 횟수:     ${feedbackCnt}
        res = feedbackRepository.countByUsers(user);
        stats.put("feedbackCnt", res);

        // 코드 리뷰 횟수:          ${codeReviewCnt}
        res = reviewsRepository.countByUsers(user);
        stats.put("codeReviewCnt", res);

        // 반영된 프로젝트 피드백 수: ${includedFeedbackCnt}
        res = selectedFeedbacksRepository.countByUsers(user);
        stats.put("includedFeedbackCnt", res);

        // 반영된 코드 리뷰 수:      ${includedCodeReviewCnt}
        res = selectedReviewsRepository.countByUsers(user);
        stats.put("includedCodeReviewCnt", res);

        // 프로젝트 리팩토링 횟수:    ${projectRefactorCnt}
        res = projectsRepository.countByProjectWriter(user) - projectsRepository.countNumByProjectWriter(user);
        stats.put("projectRefactorCnt", res);

        // 코드 리팩토링 횟수:       ${codeRefactorCnt}
        res = codesRepository.countByCodeWriter(user) - codesRepository.countNumByCodeWriter(user);
        stats.put("codeRefactorCnt", res);

        try { // 파일이 존재하면
            log.info(userName + "의 stat card ---------------");
            List<String> lines = Files.readAllLines(Paths.get(statFilePath));

            String completeStatCard = String.join("\n", lines);
            for (String key : stats.keySet()) {
                completeStatCard = completeStatCard.replace("${" + key + "}", String.valueOf(stats.get(key)));
                log.info(key + " : " + stats.get(key));
            }
            log.info("stat end ----------------------");

            return completeStatCard;
        } catch(Exception e) {
            throw new FileNotFoundException("stat card 정보 파일이 없습니다");
        }
    }

    @Override
    @Transactional
    public Pair<List<CodeInfoRes>, Boolean> getCodeList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Codes> codesPage = codesRepository.findAllByCodeWriter(usersId, pageRequest);

        return Pair.of(getCodeInfoRes(codesPage, usersRepository.findByUsersId(usersId)), codesPage.hasNext());
    }

    @Override
    @Transactional
    public Pair<List<CodeInfoRes>, Boolean> getFavoriteCodeList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Codes> codesPage = codesRepository.findAllMyFavorite(usersId, pageRequest);

        return Pair.of(getCodeInfoRes(codesPage, usersRepository.findByUsersId(usersId)), codesPage.hasNext());
    }

    @Override
    @Transactional
    public Pair<List<CodeInfoRes>, Boolean> getReviewCodeList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Codes> codesPage = codesRepository.findAllMyReviews(usersId, pageRequest);

        return Pair.of(getCodeInfoRes(codesPage, usersRepository.findByUsersId(usersId)), codesPage.hasNext());
    }

    private static List<String> getTagNames(Codes code) {
        List<String> tagList = new ArrayList<>();
        if (code.getSelectedTags() != null)
            for (CodeSelectedTags selected : code.getSelectedTags()) {
                tagList.add(selected.getTags().getName());
            }
        return tagList;
    }

    private List<CodeInfoRes> getCodeInfoRes(Page<Codes> codesPage, Users user) {
        List<Codes> codeList = codesPage.getContent();
        List<CodeInfoRes> codeInfoRes = new ArrayList<>();
        for (Codes c : codeList) {
            List<String> tagList = getTagNames(c);

            // 내가 좋아요 눌렀는지 여부
            CodeLikes codeLikes = codeLikesRepository.findByCodesAndUsers(c, user);
            Boolean liked = codeLikes != null;

            codeInfoRes.add(CodeInfoRes.builder()
                    .codeId(c.getCodesId())
                    .version(c.getVersion())
                    .title(c.getTitle())
                    .date(c.getModifiedDate())
                    .likeCnt(c.getLikeCnt())
                    .reviewCnt(c.getReviewCnt())
                    .tags(tagList)
                    .userName(c.getCodeWriter().getName())
                    .liked(liked)
                    .build()
            );
        }
        return codeInfoRes;
    }

    @Override
    @Transactional
    public Pair<List<ProjectInfoRes>, Boolean> getProjectList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Projects> projectsPage = projectsRepository.findAllByProjectWriter(usersId, pageRequest);

        return Pair.of(getProjectInfoRes(projectsPage), projectsPage.hasNext());
    }

    @Override
    @Transactional
    public Pair<List<ProjectInfoRes>, Boolean> getFavoriteProjectList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Projects> projectsPage = projectsRepository.findAllMyFavorite(usersId, pageRequest);

        return Pair.of(getProjectInfoRes(projectsPage), projectsPage.hasNext());
    }

    @Override
    @Transactional
    public Pair<List<ProjectInfoRes>, Boolean> getFeedbackProjectList(PageRequest pageRequest, Long usersId) {
        findUser(usersId);

        Page<Projects> projectsPage = projectsRepository.findAllMyFeedbacks(usersId, pageRequest);

        return Pair.of(getProjectInfoRes(projectsPage), projectsPage.hasNext());
    }

    private List<ProjectInfoRes> getProjectInfoRes(Page<Projects> projectsPage) {
        List<Projects> projectList = projectsPage.getContent();
        List<ProjectInfoRes> projectInfoRes = new ArrayList<>();
        for (Projects p : projectList) {
            List<String> tagList = getTagNames(p);

            projectInfoRes.add(ProjectInfoRes.builder()
                    .date(p.getModifiedDate())
                    .img(p.getImg())
                    .projectId(p.getProjectsId())
                    .feedbackCnt(p.getFeedbackCnt())
                    .introduction(p.getIntroduction())
                    .likeCnt(p.getLikeCnt())
                    .tags(tagList)
                    .title(p.getTitle())
                    .version(p.getVersion())
                    .closed(p.isClosed())
                    .build()
            );
        }
        return projectInfoRes;
    }

    private static List<String> getTagNames(Projects p) {
        List<String> tagList = new ArrayList<>();
        if(p.getSelectedTags() != null)
            for (ProjectSelectedTags selected : p.getSelectedTags()) {
                tagList.add(selected.getTags().getName());
            }
        return tagList;
    }

}