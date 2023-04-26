package com.cody.roughcode.project.service;

import com.cody.roughcode.code.entity.Codes;
import com.cody.roughcode.code.repository.CodesRepostiory;
import com.cody.roughcode.exception.NotMatchException;
import com.cody.roughcode.exception.NotNewestVersionException;
import com.cody.roughcode.exception.SaveFailedException;
import com.cody.roughcode.exception.UpdateFailedException;
import com.cody.roughcode.project.dto.req.FeedbackReq;
import com.cody.roughcode.project.dto.req.FeedbackUpdateReq;
import com.cody.roughcode.project.dto.res.*;
import com.cody.roughcode.project.dto.req.ProjectReq;
import com.cody.roughcode.project.dto.req.ProjectSearchReq;
import com.cody.roughcode.project.entity.*;
import com.cody.roughcode.project.repository.*;
import com.cody.roughcode.user.entity.Users;
import com.cody.roughcode.user.repository.UsersRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectsServiceImpl implements ProjectsService{

    private final S3FileServiceImpl s3FileService;

    private final UsersRepository usersRepository;
    private final ProjectsRepository projectsRepository;
    private final ProjectsInfoRepository projectsInfoRepository;
    private final ProjectSelectedTagsRepository projectSelectedTagsRepository;
    private final ProjectTagsRepository projectTagsRepository;
    private final CodesRepostiory codesRepository;
    private final FeedbacksRepository feedbacksRepository;
    private final SelectedFeedbacksRepository selectedFeedbacksRepository;
    private final ProjectFavoritesRepository projectFavoritesRepository;
    private final ProjectLikesRepository projectLikesRepository;
    private final FeedbacksLikesRepository feedbacksLikesRepository;

    @Override
    @Transactional
    public Long insertProject(ProjectReq req, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        ProjectsInfo info = ProjectsInfo.builder()
                .url(req.getUrl())
                .notice(req.getNotice())
                .content(req.getContent())
                .build();

        // 새 프로젝트를 생성하는거면 projectNum은 작성자의 projects_cnt + 1
        // 전의 프로젝트를 업데이트하는거면 projectNum은 전의 projectNum과 동일
        Long projectNum;
        int projectVersion;
        int likeCnt = 0;
        if(req.getProjectId() == -1){ // 새 프로젝트 생성
            user.projectsCntUp();
            usersRepository.save(user);

            projectNum = user.getProjectsCnt();
            projectVersion = 1;
        } else { // 기존 프로젝트 버전 업
            // num 가져오기
            // num과 user가 일치하는 max version값 가져오기
            // num과 user와 max version값에 일치하는 project 가져오기
            Projects original = projectsRepository.findByProjectsId(req.getProjectId());
            if(original == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
            original = projectsRepository.findLatestProject(original.getNum(), user.getUsersId());
            if(original == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

            if(!original.getProjectWriter().equals(user)) throw new NotMatchException();

            projectNum = original.getNum();
            projectVersion = original.getVersion() + 1;
            likeCnt = original.getLikeCnt();

            // 이전 버전 프로젝트 전부 닫기
            List<Projects> oldProjects = projectsRepository.findByNumAndProjectWriter(projectNum, user);
            for (Projects p : oldProjects) {
                p.close(true);
                projectsRepository.save(p);
            }
        }

        Long projectId = -1L;
        try {
            Projects project = Projects.builder()
                    .num(projectNum)
                    .version(projectVersion)
                    .img("temp")
                    .introduction(req.getIntroduction())
                    .title(req.getTitle())
                    .projectWriter(user)
                    .likeCnt(likeCnt)
                    .build();
            Projects savedProject = projectsRepository.save(project);
            projectId = savedProject.getProjectsId();

            // tag 등록
            if(req.getSelectedTagsId() != null)
                for(Long id : req.getSelectedTagsId()){
                    ProjectTags projectTag = projectTagsRepository.findByTagsId(id);
                    projectSelectedTagsRepository.save(ProjectSelectedTags.builder()
                            .tags(projectTag)
                            .projects(project)
                            .build());

                    projectTag.cntUp();
                    projectTagsRepository.save(projectTag);
                }
            else log.info("등록한 태그가 없습니다");

            // feedback 선택
            if(req.getSelectedFeedbacksId() != null)
                for(Long id : req.getSelectedFeedbacksId()){
                    Feedbacks feedback = feedbacksRepository.findByFeedbacksId(id);
                    if(feedback == null) throw new NullPointerException("일치하는 피드백이 없습니다");
                    if(!feedback.getProjectsInfo().getProjects().getNum().equals(projectNum))
                        throw new NullPointerException("피드백과 프로젝트가 일치하지 않습니다");
                    feedback.selectedUp();
                    feedbacksRepository.save(feedback);

                    SelectedFeedbacks selectedFeedback = SelectedFeedbacks.builder()
                            .feedbacks(feedback)
                            .projects(savedProject)
                            .build();
                    selectedFeedbacksRepository.save(selectedFeedback);
                }
            else log.info("선택한 피드백이 없습니다");

            info.setProjects(savedProject);
            projectsInfoRepository.save(info);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new SaveFailedException(e.getMessage());
        }

        return projectId;
    }

    @Override
    @Transactional
    public int updateProjectThumbnail(MultipartFile thumbnail, Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        if(thumbnail == null) throw new NullPointerException("썸네일이 등록되어있지 않습니다");

        Projects project = projectsRepository.findByProjectsId(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();
        Projects latestProject = projectsRepository.findLatestProject(project.getNum(), usersId);
        if(!project.equals(latestProject)) throw new NotNewestVersionException();

        Long projectNum = project.getNum();
        int projectVersion = project.getVersion();

        try{
            String fileName = user.getName() + "_" + projectNum + "_" + projectVersion;

            String imgUrl = s3FileService.upload(thumbnail, "project", fileName);

            project.setImgUrl(imgUrl);
            projectsRepository.save(project);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new SaveFailedException(e.getMessage());
        }

        return 1;
    }

    @Override
    public int updateProject(ProjectReq req, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 기존의 프로젝트 가져오기
        Projects target = projectsRepository.findByProjectsId(req.getProjectId());
        if(target == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        Projects latestProject = projectsRepository.findLatestProject(target.getNum(), user.getUsersId());
        if(!target.equals(latestProject)) throw new NotNewestVersionException();

        ProjectsInfo originalInfo = projectsInfoRepository.findByProjects(target);
        if(originalInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        try {
            // tag 삭제
            List<ProjectSelectedTags> selectedTagsList = target.getSelectedTags();
            if(selectedTagsList != null)
                for (ProjectSelectedTags tag : selectedTagsList) {
                    ProjectTags projectTag = tag.getTags();
                    projectTag.cntDown();
                    projectTagsRepository.save(projectTag);

                    projectSelectedTagsRepository.delete(tag);
                }
            else log.info("기존에 선택하였던 tag가 없습니다");

            // tag 등록
            if(req.getSelectedTagsId() != null)
                for(Long id : req.getSelectedTagsId()){
                    ProjectTags projectTag = projectTagsRepository.findByTagsId(id);
                    projectSelectedTagsRepository.save(ProjectSelectedTags.builder()
                            .tags(projectTag)
                            .projects(target)
                            .build());

                    projectTag.cntUp();
                    projectTagsRepository.save(projectTag);
                }
            else log.info("새로 선택한 tag가 없습니다");

            // feedback 삭제
            List<SelectedFeedbacks> selectedFeedbacksList = target.getSelectedFeedbacks();
            if(selectedFeedbacksList != null)
                for (SelectedFeedbacks feedback : selectedFeedbacksList) {
                    Feedbacks feedbacks = feedback.getFeedbacks();
                    feedbacks.selectedDown();
                    feedbacksRepository.save(feedbacks);

                    selectedFeedbacksRepository.delete(feedback);
                }
            else log.info("기존에 선택하였던 feedback이 없습니다");

            // feedback 등록
            if(req.getSelectedFeedbacksId() != null)
                for(Long id : req.getSelectedFeedbacksId()){
                    Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(id);
                    selectedFeedbacksRepository.save(SelectedFeedbacks.builder()
                            .projects(target)
                            .feedbacks(feedbacks)
                            .build());

                    feedbacks.selectedUp();
                    feedbacksRepository.save(feedbacks);
                }
            else log.info("새로 선택한 feedback이 없습니다");

            target.updateProject(req); // title, introduction 업데이트
            originalInfo.updateProject(req);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new UpdateFailedException(e.getMessage());
        }

        return 1;
    }

    @Override
    @Transactional
    public int connect(Long projectsId, Long usersId, List<Long> codesIdList) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        if(codesIdList == null || codesIdList.size() == 0) throw new NullPointerException("연결할 코드가 입력되지 않았습니다");

        Projects project = projectsRepository.findByProjectsId(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectInfo = projectsInfoRepository.findByProjects(project);
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();
        if(projectInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        // code 연결
        List<Codes> codesList = new ArrayList<>();
        for(Long id : codesIdList) {
            Codes codes = codesRepository.findByCodesId(id);
            if(codes == null) throw new NullPointerException("일치하는 코드가 존재하지 않습니다");

            codesList.add(codes);

            codes.setProject(project);
            codesRepository.save(codes);
        }
        project.setCodes(codesList);

        return codesList.size();
    }

    @Override
    public int deleteProject(Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 기존의 프로젝트 가져오기
        Projects target = projectsRepository.findByProjectsId(projectsId);
        if(target == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        Projects latestProject = projectsRepository.findLatestProject(target.getNum(), user.getUsersId());
        if(!target.equals(latestProject)) throw new NotNewestVersionException();

        ProjectsInfo originalInfo = projectsInfoRepository.findByProjects(target);
        if(originalInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        projectsInfoRepository.delete(originalInfo);

        if(target.getProjectsCodes() != null)
            for (Codes code : target.getProjectsCodes()) {
                code.setProject(null);
                codesRepository.save(code);
            }
        else log.info("연결된 코드가 없습니다");
        target.setCodes(null);
        projectsRepository.save(target);

        if(target.getSelectedTags() != null)
            for (ProjectSelectedTags selectedTag : target.getSelectedTags()) {
                ProjectTags projectTag = selectedTag.getTags();
                projectTag.cntDown();
                projectTagsRepository.save(projectTag);
                projectSelectedTagsRepository.delete(selectedTag);
            }
        else log.info("연결된 태그가 없습니다");

        // feedback 삭제
        List<SelectedFeedbacks> selectedFeedbacksList = target.getSelectedFeedbacks();
        if(selectedFeedbacksList != null)
            for (SelectedFeedbacks feedback : selectedFeedbacksList) {
                Feedbacks feedbacks = feedback.getFeedbacks();
                feedbacks.selectedDown();
                feedbacksRepository.save(feedbacks);

                selectedFeedbacksRepository.delete(feedback);
            }
        else log.info("기존에 선택하였던 feedback이 없습니다");

        projectsRepository.delete(target);

        return 1;
    }

    @Override
    public List<ProjectInfoRes> getProjectList(String sort, PageRequest pageRequest, ProjectSearchReq req) {
        String keyword = req.getKeyword();
        if(keyword == null) keyword = "";
        if(req.getTagIdList() == null || req.getTagIdList().size() == 0){ // tag 검색 x
            Page<Projects> projectsPage = null;
            if(req.getClosed())
                projectsPage = projectsRepository.findAllByKeyword(keyword, pageRequest);
            else
                projectsPage = projectsRepository.findAllOpenedByKeyword(keyword, pageRequest);

            return getProjectInfoRes(projectsPage);
        } else { // tag 검색
            Page<Projects> projectsPage = null;
            if(req.getClosed())
                projectsPage = projectSelectedTagsRepository.findAllByKeywordAndTag(keyword, req.getTagIdList(), (long) req.getTagIdList().size(), pageRequest);
            else
                projectsPage = projectSelectedTagsRepository.findAllOpenedByKeywordAndTag(keyword, req.getTagIdList(), (long) req.getTagIdList().size(), pageRequest);

            return getProjectInfoRes(projectsPage);
        }
    }

    @Override
    public ProjectDetailRes getProject(Long projectId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        Projects project = projectsRepository.findByProjectsId(projectId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        List<String> tagList = getTagNames(project);
        ProjectFavorites myFavorite = (user != null)? projectFavoritesRepository.findByProjectsAndUsers(project, user) : null;
        ProjectLikes myLike = (user != null) ? projectLikesRepository.findByProjectsAndUsers(project, user) : null;
        Boolean liked = myLike != null;
        Boolean favorite = myFavorite != null;

        ProjectDetailRes projectDetailRes = new ProjectDetailRes(project, projectsInfo, tagList, liked, favorite);

        List<Pair<Projects, ProjectsInfo>> otherVersions = new ArrayList<>();
        List<Projects> projectList = projectsRepository.findByNumAndProjectWriter(project.getNum(), project.getProjectWriter());
        for (Projects p : projectList) {
            otherVersions.add(Pair.of(p, projectsInfoRepository.findByProjects(p)));
        }

        List<VersionRes> versionResList = new ArrayList<>();
        for (Pair<Projects, ProjectsInfo> p : otherVersions) {
            List<SelectedFeedbacksRes> feedbacksResList = new ArrayList<>();
            if(p.getLeft().getSelectedFeedbacks() != null)
                for (var feedbacks : p.getLeft().getSelectedFeedbacks()) {
                    feedbacksResList.add(SelectedFeedbacksRes.builder()
                            .feedbackId(feedbacks.getFeedbacks().getFeedbacksId())
                            .content(feedbacks.getFeedbacks().getContent())
                            .build());
                }
            versionResList.add(VersionRes.builder()
                    .selectedFeedbacks(feedbacksResList)
                    .notice(p.getRight().getNotice())
                    .projectId(p.getLeft().getProjectsId())
                    .version(p.getLeft().getVersion())
                    .build());
        }
        projectDetailRes.setVersions(versionResList);

        List<FeedbackRes> feedbackResList = new ArrayList<>();
        if(projectsInfo.getFeedbacks() != null)
            for (Feedbacks f : projectsInfo.getFeedbacks()) {
                FeedbacksLikes feedbackLike = (user != null)? feedbacksLikesRepository.findByFeedbacksAndUsers(f, user) : null;
                Boolean feedbackLiked = feedbackLike != null;
                feedbackResList.add(new FeedbackRes(f, feedbackLiked));
            }
        // Selected가 우선, usersId가 같은것이 더 앞, 이후 최신순
        feedbackResList.sort(Comparator.comparing(FeedbackRes::getSelected).reversed()
                .thenComparing((f1, f2) -> {
                    if ((f1.getUserId() != null && f1.getUserId().equals(usersId)) && (f2.getUserId() == null || !f2.getUserId().equals(usersId))) {
                        return -1;
                    } else if ((f1.getUserId() == null || !f1.getUserId().equals(usersId)) && (f2.getUserId() != null && f2.getUserId().equals(usersId))) {
                        return 1;
                    } else {
                        return f2.getDate().compareTo(f1.getDate());
                    }
                }));
        projectDetailRes.setFeedbacks(feedbackResList);

        return projectDetailRes;
    }

    @Override
    public int insertFeedback(FeedbackReq req, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        Projects project = projectsRepository.findByProjectsId(req.getProjectId());
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        Feedbacks savedFeedback = feedbacksRepository.save(
                Feedbacks.builder()
                        .projectsInfo(projectsInfo)
                        .content(req.getContent())
                        .users(users)
                        .build()
        );
        projectsInfo.setFeedbacks(savedFeedback);
        projectsInfoRepository.save(projectsInfo);

        return projectsInfo.getFeedbacks().size();
    }

    @Override
    public Boolean updateFeedback(FeedbackUpdateReq req, Long userId) {
        Users users = usersRepository.findByUsersId(userId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(req.getFeedbackId());
        if(feedbacks == null) throw new NullPointerException("일치하는 피드백이 존재하지 않습니다");
        else if(feedbacks.getUsers() == null || !feedbacks.getUsers().equals(users)) throw new NotMatchException();
        else if(feedbacks.getSelected() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "채택된 피드백은 수정할 수 없습니다");

        feedbacks.editContent(req.getContent());
        Feedbacks updated = feedbacksRepository.save(feedbacks);

        return updated.getContent().equals(req.getContent());
    }

    @Override
    public List<FeedbackInfoRes> getFeedbackList(Long projectId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsId(projectId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        List<Projects> allVersion = projectsRepository.findByNumAndProjectWriter(project.getNum(), users);

        List<FeedbackInfoRes> feedbackInfoResList = new ArrayList<>();
        for (Projects p : allVersion) {
            ProjectsInfo info = projectsInfoRepository.findByProjects(p);
            List<Feedbacks> feedbacksList = info.getFeedbacks();
            for (Feedbacks f : feedbacksList) {
                feedbackInfoResList.add(new FeedbackInfoRes(f, p.getVersion(), f.getUsers()));
            }
        }

        return feedbackInfoResList;
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


