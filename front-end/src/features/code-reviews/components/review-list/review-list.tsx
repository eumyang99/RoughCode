import { Dispatch, SetStateAction } from "react";

import { FlexDiv, Text } from "@/components/elements";
import { useUser } from "@/features/auth";

import { CodeReviewSearch } from "../code-review-search";
import { Review } from "../../types";
import { CodeReviewItem } from "./review-item";
import { ReviewListWrapper, ReviewItemWrapper } from "./style";

interface CodeReviewListProps {
  reviews: Review[];
  codeId: number;
  clickedReviewId: number;
  setClickedReviewId: Dispatch<SetStateAction<number>>;
}

export const CodeReviewList = ({
  reviews,
  codeId,
  clickedReviewId,
  setClickedReviewId,
}: CodeReviewListProps) => {
  // 현재 로그인한 유저 정보(피드백이 본인 것인지 확인하기 위함)
  const userQuery = useUser();

  return (
    <ReviewListWrapper>
      <FlexDiv direction="column" width="100%" gap="1rem">
        <FlexDiv direction="column" gap="0.5rem">
          <Text color="main" bold={true} padding="1rem 0 0 0" size="0.9rem">
            이 코드에 대한 코드 리뷰 목록
          </Text>
          <Text color="font" size="0.5rem">
            스크롤, 클릭하세요!
          </Text>
        </FlexDiv>

        {/* <CodeReviewSearch /> */}
        {/* 검색을 만들게 되면 이 자리에 */}

        {reviews.length !== 0 && (
          <ReviewItemWrapper
            width="100%"
            direction="column"
            padding="1rem"
            justify="start"
          >
            {reviews.map((review) => (
              <CodeReviewItem
                review={review}
                // 익명이 아니고 로그인한 유저 닉네임과 리뷰의 유저네임이 같을 때
                isMine={Boolean(
                  review.userName.length !== 0 &&
                    userQuery.data?.nickname === review.userName
                )}
                showDetails={Boolean(review.reviewId === clickedReviewId)}
                codeId={codeId}
                setClickedReviewId={setClickedReviewId}
                key={review.reviewId}
              />
            ))}
          </ReviewItemWrapper>
        )}
      </FlexDiv>
    </ReviewListWrapper>
  );
};
